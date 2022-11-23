/*
 *  Hubitat BWA Spa Manager
 *  -> Pump Device Driver
 *
 *  Copyright 2022 Jonathan Drake
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  CHANGE HISTORY
 *  VERSION     DATE            NOTES
 *  0.0.1       2022-06-21      First release, based on switch from Richard Powell
 *  0.0.2       2022-11-22      Added logging
 *
 */

import groovy.transform.Field

@Field static int LOG_LEVEL = 3

@Field static String NAMESPACE = "drakej"

@Field static String SWITCH_CHILD_DEVICE_NAME_PREFIX = "BWA SPA - Pump"

metadata {
    definition (name: SWITCH_CHILD_DEVICE_NAME_PREFIX, namespace: NAMESPACE, author: "Jonathan Drake") {
        capability "Refresh"
        
        attribute "pump", "enum", ["off", "low", "high"]
        attribute "balboaAPIButtonNumber", "number"
        
        command "toggle"
    }
}

def logMessage(level, message) {
    if (level >= LOG_LEVEL) {
        if (level < 3) {
            log.debug message
        } else {
            log.info message
        }
    }
}

void parse(input) {
    logMessage(2, "Pump input: '${input}'")
    switch (input) {
        case "low":
            sendEvent(name: "pump", value: "low")
            break;
        case "high":
            sendEvent(name: "pump", value: "high")
            break;
        case "off":
            sendEvent(name: "pump", value: "off")
    }
}

void installed() {
}

void setBalboaAPIButtonNumber(balboaAPIButtonNumber) {
    sendEvent(name: "balboaAPIButtonNumber", value: balboaAPIButtonNumber)
}

void toggle() {
    if (device.currentValue("pump", true) == "off")
    {
        sendEvent(name: "pump", value: "low")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    } else if (device.currentValue("pump", true) == "low") {
        sendEvent(name: "pump", value: "high")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    } else {
        sendEvent(name: "pump", value: "off")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    }
}

def refresh() {
    parent?.refresh()
}