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

package com.google.fleetevents.mocks;

import com.google.fleetevents.FleetEventCreatorBase;
import com.google.fleetevents.common.database.FirestoreDatabaseClient;
import com.google.fleetevents.common.util.FleetEngineClient;
import com.google.fleetevents.lmfs.models.DeliveryTaskData;
import com.google.fleetevents.lmfs.models.DeliveryVehicleData;
import java.util.HashMap;
import java.util.Map;
import org.mockito.Mockito;

/** Mock fleet events creator for use in testing fleet event creator. */
public class MockFleetEventCreator extends FleetEventCreatorBase {

  public static Map<String, DeliveryTaskData> mockTasks = new HashMap<>();
  public static Map<String, DeliveryVehicleData> mockVehicles = new HashMap<>();

  static FirestoreDatabaseClient firestore;
  static FleetEngineClient fleetEngineClient;

  @Override
  public FirestoreDatabaseClient getDatabase() {
    if (firestore == null) {
      firestore = Mockito.mock(FirestoreDatabaseClient.class);
    }
    return firestore;
  }

  @Override
  public FleetEngineClient getFleetEngineClient() {
    if (fleetEngineClient == null) {
      fleetEngineClient = Mockito.mock(FleetEngineClient.class);
    }
    return fleetEngineClient;
  }
}
