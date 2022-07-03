/*
 *  Hubitat BWA Spa Manager
 *  -> Blower Device Driver
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
 *  0.0.1       2022-06-28      First release, based on switch from Richard Powell
 *
 */

import groovy.transform.Field


@Field static String NAMESPACE = "drakej"

@Field static String SWITCH_CHILD_DEVICE_NAME_PREFIX = "BWA SPA - Blower"

metadata {
    definition (name: SWITCH_CHILD_DEVICE_NAME_PREFIX, namespace: NAMESPACE, author: "Jonathan Drake") {
        capability "Refresh"
        
        attribute "blower", "enum", ["off", "low", "medium", "high"]
        attribute "balboaAPIButtonNumber", "number"
        
        command "toggle"
    }
}

void parse(input) {
    log.info "Blower input: '${input}'"
    switch (input) {
        case "low":
            sendEvent(name: "blower", value: "low")
            break;
        case "medium":
            sendEvent(name: "blower", value: "medium")
            break;
        case "high":
            sendEvent(name: "blower", value: "high")
            break;
        case "off":
            sendEvent(name: "blower", value: "off")
    }
}

void setBalboaAPIButtonNumber(balboaAPIButtonNumber) {
    sendEvent(name: "balboaAPIButtonNumber", value: balboaAPIButtonNumber)
}

void toggle() {
    if (device.currentValue("blower", true) == "off")
    {
        sendEvent(name: "blower", value: "low")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    } else if (device.currentValue("blower", true) == "low") {
        sendEvent(name: "blower", value: "high")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    } else if (device.currentValue("blower", true) == "medium") {
        sendEvent(name: "blower", value: "high")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    } else {
        sendEvent(name: "blower", value: "off")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    }
}

def refresh() {
    parent?.refresh()
}