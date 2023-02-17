/*
 *  Hubitat BWA Spa Manager
 *  -> Switch Device Driver
 *
 *  Copyright 2022 Jonathan Drake
 *  Copyright 2020 Richard Powell
 *
 *  Licensed under the The MIT License (MIT); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      https://mit-license.org/
 *
 *  CHANGE HISTORY
 *  VERSION     DATE            NOTES
 *  0.0.1       2020-10-11      First release
 *  0.0.2       2022-11-22      Added logging
 *  0.0.3       2022-11-24      Moved logging to shared library
 *
 */

import groovy.transform.Field

#include drakej.logmagic

@Field static String NAMESPACE = "drakej"

@Field static String SWITCH_CHILD_DEVICE_NAME_PREFIX = "BWA SPA - Switch"

metadata {
    definition (name: SWITCH_CHILD_DEVICE_NAME_PREFIX, namespace: NAMESPACE, author: "Richard Powell") {
        capability "Switch"
        capability "Refresh"
        
        attribute "switch", "enum", ["on", "off"]
        attribute "balboaAPIButtonNumber", "number"
        
        command "on"
        command "off"
    }
}

void parse(input) {
    logMessage(2, "Switch input: '${input}'")
    switch (input) {
        case "on":
        case "true":
            sendEvent(name: "switch", value: "on")
            break;
        case "off":
        case "false":
            sendEvent(name: "switch", value: "off")
            break;
    }
    
}

void installed() {
}

void setBalboaAPIButtonNumber(balboaAPIButtonNumber) {
    sendEvent(name: "balboaAPIButtonNumber", value: balboaAPIButtonNumber)
}

void on() {
    if (device.currentValue("switch", true) != "on")
    {
        sendEvent(name: "switch", value: "on")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    }
}

void off() {
    if (device.currentValue("switch", true) != "off")
    {
        sendEvent(name: "switch", value: "off")
        parent?.sendCommand("Button", device.currentValue("balboaAPIButtonNumber"))
    }
}

def refresh() {
    parent?.refresh()
}