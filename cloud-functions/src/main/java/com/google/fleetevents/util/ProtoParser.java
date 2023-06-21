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

package com.google.fleetevents.util;

import com.google.fleetevents.models.pubsub.PubSubBody;
import com.google.gson.Gson;
import com.google.logging.v2.LogEntry;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import io.cloudevents.CloudEventData;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses log entries from Cloud Logging to protobufs of the original requests.
 */
public class ProtoParser {

  static HashMap<String, Integer> enumStringsMap = new HashMap<>();

  static {
    enumStringsMap.put("TASK_OUTCOME_LOG_UNSPECIFIED", 0);
    enumStringsMap.put("TASK_OUTCOME_LOG_SUCCEEDED", 1);
    enumStringsMap.put("TASK_OUTCOME_LOG_FAILED", 2);

    enumStringsMap.put("TASK_STATE_LOG_UNSPECIFIED", 0);
    enumStringsMap.put("TASK_STATE_LOG_OPEN", 1);
    enumStringsMap.put("TASK_STATE_LOG_CLOSED", 2);

    enumStringsMap.put("VEHICLE_STOP_STATE_LOG_UNSPECIFIED", 0);
    enumStringsMap.put("VEHICLE_STOP_STATE_LOG_NEW", 1);
    enumStringsMap.put("VEHICLE_STOP_STATE_LOG_ENROUTE", 2);
    enumStringsMap.put("VEHICLE_STOP_STATE_LOG_ARRIVED", 3);

    enumStringsMap.put("NAVIGATION_STATUS_LOG_UNSPECIFIED", 0);
    enumStringsMap.put("NAVIGATION_STATUS_NO_GUIDANCE", 1);
    enumStringsMap.put("NAVIGATION_STATUS_ENROUTE_TO_DESTINATION", 2);
    enumStringsMap.put("NAVIGATION_STATUS_OFF_ROUTE", 3);
    enumStringsMap.put("NAVIGATION_STATUS_ARRIVED_AT_DESTINATION", 4);
  }

  public static <T extends Message> T parseLogEntryResponse(LogEntry logEntry, T message)
      throws InvalidProtocolBufferException {
    Message.Builder builder = message.toBuilder();
    Map<String, Value> jsonStruct = logEntry.getJsonPayload().getFieldsMap();
    if (! jsonStruct.containsKey("response")) {
      throw new IllegalArgumentException("Received log entry with empty response: " + logEntry.getLogName());
    }
    Struct response = jsonStruct.get("response").getStructValue();
    String json = getJson(response);
    parseJson(json, builder);
    return (T) builder.build();
  }

  public static <T extends Message> T parseLogEntryRequest(LogEntry logEntry, T message)
      throws InvalidProtocolBufferException {
    Message.Builder builder = message.toBuilder();
    Map<String, Value> jsonStruct = logEntry.getJsonPayload().getFieldsMap();
    if (! jsonStruct.containsKey("request")) {
      throw new IllegalArgumentException("Received log entry with empty request: " + logEntry.getLogName());
    }
    Struct request = jsonStruct.get("request").getStructValue();
    String json = getJson(request);
    parseJson(json, builder);
    return (T) builder.build();
  }

  public static LogEntry cloudEventDataToLogEntry(CloudEventData cloudEventData)
      throws InvalidProtocolBufferException {
    LogEntry.Builder logEntryBuilder = LogEntry.newBuilder();
    String pubSubData = new String(cloudEventData.toBytes(), StandardCharsets.UTF_8);
    Gson gson = new Gson();
    PubSubBody body = gson.fromJson(pubSubData, PubSubBody.class);
    // Retrieve and decode PubSub message data
    String encodedData = body.getMessage().getData();
    String decodedData =
        new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
    JsonFormat.parser().ignoringUnknownFields().merge(decodedData, logEntryBuilder);
    return logEntryBuilder.build();
  }

  private static String getJson(Struct struct) throws InvalidProtocolBufferException {
    String json = JsonFormat.printer().print(struct);
    return json;
  }

  private static <T extends Message.Builder> void parseJson(String json, T messageType)
      throws InvalidProtocolBufferException {
    for (var enumKey : enumStringsMap.keySet()) {
      json = json.replace(enumKey, String.valueOf(enumStringsMap.get(enumKey)));
    }
    JsonFormat.parser().ignoringUnknownFields().merge(json, messageType);
  }
}
