/*
 *  Hubitat BWA Spa Manager
 *  -> Thermostat Device Driver
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
import groovy.time.TimeCategory

#include drakej.logmagic

@Field static String NAMESPACE = "drakej"

@Field static String THERMOSTAT_CHILD_DEVICE_NAME_PREFIX = "BWA SPA - Thermostat"

metadata {
    definition (name: THERMOSTAT_CHILD_DEVICE_NAME_PREFIX, namespace: NAMESPACE, author: "Richard Powell") {
        capability "Thermostat"
        capability "Refresh"
        
        attribute "supportedThermostatFanModes", "enum", ["circulate"]
        attribute "supportedThermostatModes", "enum", ["off", "heat"]
        attribute "thermostatFanMode", "string"
        attribute "thermostatOperatingState", "string"
        attribute "temperature", "number"

        command "heat"
        command "setThermostatMode"
        command "setHeatingSetpoint"
        command "getTemperatureRange"
        
        preferences {
            input "defaultOnTemperature", "number", title: "Default Temperature When Turned On", range: getTemperatureRange()
        }
    }
}

void sendEvents(List<Map> events) {
    events.each {
        sendEvent(name: it.name, value: it.value)
    }
}

void sendEventsWithStateChange(List<Map> events) {
    events.each {
        sendEvent(name: it.name, value: it.value, isStateChange: true)
    }
}

void sendEventsWithUnits(List<Map> events) {
    events.each {
        sendEvent(name: it.name, value: it.value, unit: it.unit)
    }
}

void installed() {
    sendEventsWithStateChange([
        [name:"supportedThermostatFanModes", value: ["circulate"]],
        [name:"supportedThermostatModes", value: ["off", "heat"]],
        [name:"thermostatFanMode", value: "circulate"]
    ])
}

void heat() {
    setThermostatMode("heat")
}

void setThermostatMode(mode) {
    // TODO: Throw an exception if we're asked to set the thermostat to a mode we don't support (e.g. "cool")
    sendEvent([name: "thermostatMode", value: mode])
}

void setHeatingSetpoint(setpoint) {
    sendEvent(name: "heatingSetpoint", value: setpoint)
    parent?.sendCommand("SetTemp", device.currentValue("temperatureScale") == "C" ? setpoint * 2 : setpoint)
}

def getTemperatureRange() {
    return "(26.5..105)"
}

def refresh() {
    parent?.refresh()
}