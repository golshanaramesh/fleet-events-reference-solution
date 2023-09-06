package com.google.fleetevents.odrd.models.outputs;

import com.google.cloud.Timestamp;
import com.google.fleetevents.common.models.OutputEvent;
import java.util.Objects;

public class EtaAbsoluteChangeOuputEvent extends OutputEvent {

  private Timestamp originalEta;
  private Timestamp newEta;
  private long thresholdMilliseconds;

  private boolean isTripOutputEvent;

  private Timestamp eventTimestamp;

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  private String identifier;

  public EtaAbsoluteChangeOuputEvent() {
    type = Type.ETA;
  }

  public Timestamp getOriginalEta() {
    return originalEta;
  }

  public void setOriginalEta(Timestamp originalEta) {
    this.originalEta = originalEta;
  }

  public Timestamp getNewEta() {
    return newEta;
  }

  public void setNewEta(Timestamp newEta) {
    this.newEta = newEta;
  }

  public long getThresholdMilliseconds() {
    return thresholdMilliseconds;
  }

  public void setThresholdMilliseconds(long thresholdMilliseconds) {
    this.thresholdMilliseconds = thresholdMilliseconds;
  }

  public boolean getIsTripOutputEvent() {
    return isTripOutputEvent;
  }

  public void setIsTripOutputEvent(boolean tripOutputEvent) {
    isTripOutputEvent = tripOutputEvent;
  }

  public Timestamp getEventTimestamp() {
    return eventTimestamp;
  }

  public void setEventTimestamp(Timestamp eventTimestamp) {
    this.eventTimestamp = eventTimestamp;
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof EtaAbsoluteChangeOuputEvent that) {
      return Objects.equals(this.fleetEvent, that.fleetEvent)
          && Objects.equals(this.newEta, that.newEta)
          && Objects.equals(this.originalEta, that.originalEta)
          && Objects.equals(this.thresholdMilliseconds, that.thresholdMilliseconds)
          && Objects.equals(this.identifier, that.identifier)
          && Objects.equals(this.eventTimestamp, that.eventTimestamp)
          && Objects.equals(this.type, that.type);
    }
    return false;
  }

  @Override
  public String toString() {
    return "EtaAbsoluteChangeOuputEvent{"
        + "originalEta="
        + originalEta
        + ", newEta="
        + newEta
        + ", thresholdMiliseconds="
        + thresholdMilliseconds
        + ", triggerId="
        + identifier
        + ", eventTimestamp="
        + eventTimestamp
        + ", fleetEvent="
        + fleetEvent
        + ", type="
        + type
        + '}';
  }
}
