/*
 *  Hubitat BWA Spa Manager
 *  -> Pump Device Driver
 *
 *  Copyright 2022 Jonathan Drake
 *
 *  Licensed under the The MIT License (MIT); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      https://mit-license.org/
 *
 *  CHANGE HISTORY
 *  VERSION     DATE            NOTES
 *  0.0.1       2022-06-21      First release, based on switch from Richard Powell
 *  0.0.2       2022-11-22      Added logging
 *  0.0.3       2022-11-24      Moved logging to shared library
 *
 */

import groovy.transform.Field

#include drakej.logmagic

@Field static String NAMESPACE = "drakej"

@Field static String SWITCH_CHILD_DEVICE_NAME_PREFIX = "BWA SPA - Pump"

metadata {
    definition (name: SWITCH_CHILD_DEVICE_NAME_PREFIX, namespace: NAMESPACE, author: "Jonathan Drake") {
        capability "Refresh"
        capability "PushableButton"
        
        attribute "pump", "enum", ["off", "low", "high"]
        attribute "balboaAPIButtonNumber", "number"
        attribute "numberOfButtons", "number"
        
        command "push"
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

void push(buttonNumber) {
    logMessage(2, "Received a push for button ${buttonNumber}")
    if (device.currentValue("pump", true) == "off")
    {
        sendEvent(name: "pump", value: "low")
        logMessage(3, "Going to set pump to low")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    } else if (device.currentValue("pump", true) == "low") {
        sendEvent(name: "pump", value: "high")
        logMessage(3, "Going to set pump to high")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    } else {
        sendEvent(name: "pump", value: "off")
        logMessage(3, "Going to set pump to off")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    }
}

def refresh() {
    parent?.refresh()
}