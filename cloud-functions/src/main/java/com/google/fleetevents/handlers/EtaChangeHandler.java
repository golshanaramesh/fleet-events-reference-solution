/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.fleetevents.handlers;

import com.google.cloud.firestore.Transaction;
import com.google.fleetevents.FleetEventHandler;
import com.google.fleetevents.database.FirestoreDatabaseClient;
import com.google.fleetevents.models.DeliveryVehicleData;
import com.google.fleetevents.models.DeliveryVehicleFleetEvent;
import com.google.fleetevents.models.FleetEvent;
import com.google.fleetevents.models.TaskInfo;
import com.google.fleetevents.models.VehicleJourneySegment;
import com.google.fleetevents.models.outputs.DistanceRemainingOutputEvent;
import com.google.fleetevents.models.outputs.EtaOutputEvent;
import com.google.fleetevents.models.outputs.OutputEvent;
import com.google.fleetevents.util.TimeUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FleetEventHandler that alerts when the eta changes by either an absolute or relative amount to
 * the original recorded ETA. Sets the original ETA from the vehicle's duration at assignment time.
 */
public class EtaChangeHandler implements FleetEventHandler {

  private static final long SECONDS_TO_MILLIS = TimeUtil.SECONDS_TO_MILLIS;
  private static final int MINUTES_TO_SECONDS = 60;
  // TODO: turn into a flag
  private static final long ETA_THRESHOLD_MILLIS = 5 * MINUTES_TO_SECONDS * SECONDS_TO_MILLIS;

  // percent change from original duration tolerated.
  private static final float RELATIVE_ETA_THRESHOLD = 0.5F;

  private static final String ETA_CHANGE_METADATA_ID = "etaChange";
  private static final String RELATIVE_ETA_CHANGE_METADATA_ID = "relativeEtaChange";

  @Override
  public List<OutputEvent> handleEvent(FleetEvent fleetEvent, Transaction transaction) {
    DeliveryVehicleFleetEvent deliveryVehicleFleetEvent = (DeliveryVehicleFleetEvent) fleetEvent;
    var oldDeliveryVehicle = deliveryVehicleFleetEvent.oldDeliveryVehicle();
    var newDeliveryVehicle = deliveryVehicleFleetEvent.newDeliveryVehicle();

    List<VehicleJourneySegment> vehicleJourneySegments =
        newDeliveryVehicle.getRemainingVehicleJourneySegments();
    List<OutputEvent> outputEvents = new ArrayList<>();
    long cumulativeDuration = 0;
    for (int i = 0; i < vehicleJourneySegments.size(); i++) {
      VehicleJourneySegment vehicleJourneySegment = vehicleJourneySegments.get(i);
      long journeyDuration =
          i == 0 ? newDeliveryVehicle.getRemainingDuration() : vehicleJourneySegment.getDuration();
      List<TaskInfo> taskInfos =
          vehicleJourneySegment.getVehicleStop() != null
              ? vehicleJourneySegment.getVehicleStop().getTaskInfos()
              : Collections.emptyList();
      cumulativeDuration = cumulativeDuration + journeyDuration;
      for (TaskInfo taskInfo : taskInfos) {
        String taskId = taskInfo.getTaskId();
        cumulativeDuration = cumulativeDuration + taskInfo.getTaskDuration();
        Long originalEta = getOriginalEta(oldDeliveryVehicle, taskId);
        // do not allow original ETAs of 0
        if (originalEta == null && cumulativeDuration != 0) {
          originalEta = newDeliveryVehicle.getEventTimestamp() + cumulativeDuration;
          setOriginalEta(newDeliveryVehicle, taskId, originalEta);
          setOriginalDuration(newDeliveryVehicle, taskId, cumulativeDuration);
          EtaOutputEvent etaOutputEvent =
              new EtaOutputEvent.Builder()
                  .setType(OutputEvent.Type.ETA)
                  .setOriginalEta(originalEta)
                  .setNewEta(originalEta)
                  .setOriginalDuration(cumulativeDuration)
                  .setDelta(0L)
                  .setRelativeDelta(0.0F)
                  .setTaskId(taskId)
                  .setFleetEvent(fleetEvent)
                  .build();
          outputEvents.add(etaOutputEvent);
          EtaOutputEvent relativeEtaOutputEvent =
              new EtaOutputEvent.Builder(etaOutputEvent)
                  .setType(OutputEvent.Type.RELATIVE_ETA)
                  .build();
          outputEvents.add(relativeEtaOutputEvent);
        } else if (originalEta != null) {
          Long originalDuration = getOriginalDuration(oldDeliveryVehicle, taskId);
          Long newEta = newDeliveryVehicle.getEventTimestamp() + cumulativeDuration;
          EtaOutputEvent etaOutputEvent =
              new EtaOutputEvent.Builder()
                  .setOriginalEta(originalEta)
                  .setOriginalDuration(originalDuration)
                  .setNewEta(newEta)
                  .setDelta(newEta - originalEta)
                  .setRelativeDelta(
                      (float) (cumulativeDuration - originalDuration) / originalDuration)
                  .setTaskId(taskId)
                  .setFleetEvent(fleetEvent)
                  .build();
          if (etaThresholdReached(originalEta, newEta)) {
            outputEvents.add(
                new EtaOutputEvent.Builder(etaOutputEvent)
                    .setType(EtaOutputEvent.Type.ETA)
                    .build());
          }
          if (relativeEtaThresholdReached(originalDuration, cumulativeDuration)) {
            outputEvents.add(
                new EtaOutputEvent.Builder(etaOutputEvent)
                    .setType(EtaOutputEvent.Type.RELATIVE_ETA)
                    .build());
          }
        }
      }
    }
    return outputEvents;
  }

  @Override
  public boolean respondsTo(
      FleetEvent fleetEvent,
      Transaction transaction,
      FirestoreDatabaseClient firestoreDatabaseClient) {
    if (fleetEvent.getEventType() != FleetEvent.Type.DELIVERY_VEHICLE_FLEET_EVENT) {
      return false;
    }
    DeliveryVehicleFleetEvent deliveryVehicleFleetEvent = (DeliveryVehicleFleetEvent) fleetEvent;
    // this filter exists because it's possible for durations to be assigned when there is no
    // vehicle id.
    return deliveryVehicleFleetEvent.vehicleDifferences().containsKey("remainingDuration")
            && deliveryVehicleFleetEvent.newDeliveryVehicle().getDeliveryVehicleId() != null
            && deliveryVehicleFleetEvent.newDeliveryVehicle().getRemainingDuration() != null;
  }

  @Override
  public boolean verifyOutput(OutputEvent outputEvent) {
    if (! (outputEvent instanceof EtaOutputEvent)) {
      return false;
    }
    if (outputEvent.getType() != OutputEvent.Type.ETA
            && outputEvent.getType() != OutputEvent.Type.RELATIVE_ETA) {
      return false;
    }
    return true;
  }

  private Long getOriginalEta(DeliveryVehicleData deliveryVehicleData, String taskId) {
    String id = deliveryVehicleData.getDeliveryVehicleId();
    Map<String, Object> eventMetadata = deliveryVehicleData.getEventMetadata();
    if (!eventMetadata.containsKey(ETA_CHANGE_METADATA_ID)) {
      return null;
    }
    Map<String, Map<String, Long>> vehicleIdToEtaMetadata =
        (Map<String, Map<String, Long>>) eventMetadata.get(ETA_CHANGE_METADATA_ID);
    if (!vehicleIdToEtaMetadata.containsKey(id)) {
      return null;
    }
    Map<String, Long> metadata = vehicleIdToEtaMetadata.get(id);
    if (!metadata.containsKey(taskId)) {
      return null;
    }
    return metadata.get(taskId);
  }

  private void setOriginalEta(DeliveryVehicleData deliveryVehicleInfo, String taskId, Long eta) {
    String id = deliveryVehicleInfo.getDeliveryVehicleId();
    deliveryVehicleInfo
        .getEventMetadata()
        .putIfAbsent(ETA_CHANGE_METADATA_ID, new HashMap<String, Map<String, Long>>());
    Map<String, Map<String, Long>> vehicleIdToEtaMetadata =
        (Map<String, Map<String, Long>>)
            deliveryVehicleInfo.getEventMetadata().get(ETA_CHANGE_METADATA_ID);
    vehicleIdToEtaMetadata.putIfAbsent(id, new HashMap<String, Long>());
    Map<String, Long> metadata = vehicleIdToEtaMetadata.get(id);
    metadata.put(taskId, eta);
  }

  private Long getOriginalDuration(DeliveryVehicleData deliveryVehicleInfo, String taskId) {
    String id = deliveryVehicleInfo.getDeliveryVehicleId();
    Map<String, Object> eventMetadata = deliveryVehicleInfo.getEventMetadata();
    if (!eventMetadata.containsKey(RELATIVE_ETA_CHANGE_METADATA_ID)) {
      return null;
    }
    Map<String, Map<String, Long>> vehicleIdToEtaMetadata =
        (Map<String, Map<String, Long>>) eventMetadata.get(RELATIVE_ETA_CHANGE_METADATA_ID);
    if (!vehicleIdToEtaMetadata.containsKey(id)) {
      return null;
    }
    Map<String, Long> metadata = vehicleIdToEtaMetadata.get(id);
    if (!metadata.containsKey(taskId)) {
      return null;
    }
    return metadata.get(taskId);
  }

  private void setOriginalDuration(
      DeliveryVehicleData deliveryVehicleInfo, String taskId, Long duration) {
    String id = deliveryVehicleInfo.getDeliveryVehicleId();
    deliveryVehicleInfo
        .getEventMetadata()
        .putIfAbsent(
            RELATIVE_ETA_CHANGE_METADATA_ID, new HashMap<String, Map<String, Long>>());
    HashMap<String, Map<String, Long>> vehicleIdToEtaMetadata =
        (HashMap<String, Map<String, Long>>)
            deliveryVehicleInfo.getEventMetadata().get(RELATIVE_ETA_CHANGE_METADATA_ID);
    vehicleIdToEtaMetadata.putIfAbsent(id, new HashMap<String, Long>());
    Map<String, Long> metadata = vehicleIdToEtaMetadata.get(id);
    metadata.put(taskId, duration);
  }

  private boolean etaThresholdReached(Long oldEta, Long newEta) {
    return Math.abs(newEta - oldEta) > ETA_THRESHOLD_MILLIS;
  }

  private boolean relativeEtaThresholdReached(Long oldDuration, Long newDuration) {
    // handle zero case
    if (oldDuration == 0 && newDuration == 0) {
      return 0 > RELATIVE_ETA_THRESHOLD;
    }
    if (oldDuration == 0 && newDuration != 0) {
      return true;
    }
    return (Math.abs((float) (newDuration - oldDuration) / oldDuration)) > RELATIVE_ETA_THRESHOLD;
  }
}
