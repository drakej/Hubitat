/*
 *  Hubitat BWA Spa
 *  -> Parent Device Driver
 *
 *  Copyright 2022 Jonathan Drake
 *  Copyright 2020 Richard Powell
 *   based on work Copyright 2020 Nathan Spencer that he did for SmartThings
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
 *  0.9.0       2020-01-30      Initial release with basic access and control of spas
 *  1.0.0       2020-01-31      Updated UI and icons as well as switch functionality that can be controlled with
 *                              Alexa. Added preference for a "Default Temperature When Turned On"
 *  1.1.0       2020-06-03      Additional functionality for aux, temperature range, and heat modes
 *  1.1.1       2020-07-26      Adjusted icons to better match functionality for aux, temperature range and heat modes
 *                              and removed duplicate tile declaration
 *  1.1.2b      2020-09-17      Modified / validated to work on Hubitat
 *  1.1.3       2020-10-11      Major rewrite of this driver to work with Hubitat's Parent-Child device driver model
 *  1.1.4       2020-10-11      Support the remaining device types except Blower, more code clean-up
 *  2.0.0       2022-06-28      Revamping since there's a lot of missing functionality but overall a great base from Richard and Nathan
 *
 */

import groovy.transform.Field
import groovy.time.TimeCategory

@Field static int LOG_LEVEL = 3

@Field static String NAMESPACE = "drakej"

@Field static String DEVICE_NAME_PREFIX = "BWA SPA"
@Field static String PARENT_DEVICE_NAME = "BWA SPA - Parent"


metadata {
    definition (name: PARENT_DEVICE_NAME, namespace: NAMESPACE, author: "Jonathan Drake") {
        capability "Refresh"
                        
        /* This is a list of attributes sent to us right after we successfully login
         * to Balboa and pull details about Spas linked to the user's account.
         *
         * Hubitat requires attributes to be defined in order for sendEvent(...) to
         * be able to update that attribute.
         */
        attribute "create_user_id", "string"
        attribute "deviceId", "string" // renamed from "device_id"
        attribute "update_user_id", "string"
        attribute "updated_at", "string"
        attribute "__v", "string"
        attribute "active", "string"
        attribute "created_at", "string"
        attribute "_id", "string"
        
        // Additional attributes
        attribute "spaStatus", "string"
    }   
}

@Field static Map BUTTON_MAP = [
    Pump0: 61,
    Pump1: 4,
    Pump2: 5,
    Pump3: 6,
    Pump4: 7,
    Pump5: 8,
    Pump6: 9,
    Pump7: 10,
    Pump8: 11,
    Blower1: 12,
    Blower2: 13,
    Mister1: 14,
    Mister2: 15,
    Mister3: 16,
    Light1: 17,
    Light2: 18,
    Light3: 19,
    Light4: 20,
    Aux1: 22,
    Aux2: 23,
    Aux3: 24,
    Aux4: 25,
    TempRange: 80,
    HeatMode: 81]

def logMessage(level, message) {
    if (level >= LOG_LEVEL) {
        if (level < 3) {
            log.debug message
        } else {
            log.info message
        }
    }
}

def installed() {
}

def updated() {
}

def on() {
    // TODO: Maybe implement some sort of "Turn everything on" feature.
}

def off() {
    // TODO: Implement a "Turn everything off" feature.
}

def sendCommand(action, data) {
    parent.sendCommand(device.currentValue("deviceId"), action, data)
    runIn(2, refresh)
}

def parseDeviceData(Map results) {
    results.each {name, value ->
        sendEvent(name: name, value: value, displayed: true)
    }
}

def createChildDevices(spaConfiguration) {
    // Thermostat
    fetchChild(true, "Thermostat", "Thermostat")
 
    /* The incoming spaConfiguration has a list of all the possible add-on devices like
       pumps, lights, etc. mapped to a boolean indicating whether or not this particular
       hot tub actually has that specific device installed on it.

       Iterate through all the possible add-on devices and if the hot tub we're working
       with actually has that device installed on it then we will go ahead and create a
       child device for it (passing "true" as the first parameter to fetchChild(...) will
       have it go and create a device if it doesn't exist already.
    */
    log.info "Spa Configuration: ${spaConfiguration}"
    spaConfiguration.each { key, value ->
        if (key.startsWith("Pump") && value == true) {
            def pumpNumber = key[4].toInteger()
            fetchChild(true, "Pump", "Pump ${pumpNumber}", BUTTON_MAP[key])
        }

        if (key.startsWith("Light") && value == true) {
            def lightNumber = key[5].toInteger()
            fetchChild(true, "Switch", "Light ${lightNumber}", BUTTON_MAP[key])
        }

        if (key.startsWith("Blower") && value == true) {
            def blowerNumber = key[6].toInteger()
            fetchChild(true, "Blower", "Blower ${blowerNumber}", BUTTON_MAP[key])
        }
 
        if (key.startsWith("Mister") && value == true) {
            def misterNumber = key[6].toInteger()
            fetchChild(true, "Switch", "Mister ${misterNumber}", BUTTON_MAP[key])
        }
            
        if (key.startsWith("Aux") && value == true) {
            def misterNumber = key[6].toInteger()
            fetchChild(true, "Switch", "Aux ${auxNumber}", BUTTON_MAP[key])
        }
    }
}

def parsePanelData(encodedData) {
    byte[] decoded = encodedData.decodeBase64()

    def is24HourTime = (decoded[13] & 2) != 0 ? true : false
    def currentTimeHour = decoded[7]
    def currentTimeMinute = decoded[8]
    
    def temperatureScale = (decoded[13] & 1) == 0 ? "F" : "C"
    def actualTemperature = decoded[6]
    
    def targetTemperature = decoded[24]
    def isHeating = (decoded[14] & 48) != 0
    def heatingMode = (decoded[14] & 4) == 4 ? "high" : "low"
    def heatMode
    switch (decoded[9]) {
        case 0:
            heatMode = "Ready"
            break;
        case 1:
            heatMode = "Rest"
            break;
        case 2:
            heatMode = "Ready in Rest"
            break;
        default:
            heatMode = "None"
    }
    
    // Send events to Thermostat child device
    def thermostatChildDevice = fetchChild(false, "Thermostat", "Thermostat")
    if (thermostatChildDevice != null) {
        log.info "Temperature being set: ${actualTemperature}"
        thermostatChildDevice.sendEventsWithUnits([
            [name: "temperature", value: actualTemperature, unit: temperatureScale],
            [name: "heatingSetpoint", value: targetTemperature, unit: temperatureScale]
        ])
        thermostatChildDevice.sendEvents([
            [name: "thermostatMode", value: isHeating ? "heat" : "off"],
            [name: "thermostatOperatingState", value: isHeating ? "heating" : "idle"],
        ])
    }
      
    def filtermode
    switch (decoded[13] & 12) {
        case 4:
            filterMode = "Filter 1"
            break;
        case 8:
            filterMode = "Filter 2"
            break;
        case 12:
            filterMode = "Filter 1 & 2"
            break;
        case 0:
        default:
            filterMode = "Off"
    }
    
    def accessibilityType
    switch (decoded[13] & 48) {
        case 16:
            accessibilityType = "Pump Light"
            break;
        case 32:
        case 42:
            accessibilityType = "None"
            break;
        default:
            accessibilityType = "All"
    }
    
    // Pumps
    def pumpState = []
    pumpState[0] = null
    def pump1ChildDevice = fetchChild(false, "Pump", "Pump 1")
    if (pump1ChildDevice != null) {
        switch (decoded[15] & 3) { // Pump 1
            case 1:
            	pumpState[1] = "low"
                break
            case 2:
            	pumpState[1] = "high"
                break
            default:
            	pumpState[1] = "off"
        }
        pump1ChildDevice.parse(pumpState[1])
    }
    def pump2ChildDevice = fetchChild(false, "Pump", "Pump 2")
    if (pump2ChildDevice != null) {
        switch (decoded[15] & 12) { // Pump 2
            case 4:
                pumpState[2] = "low"
                break
            case 8:
                pumpState[2] = "high"
                break
            default:
                pumpState[2] = "off"
        }
        pump2ChildDevice.parse(pumpState[2])
    }
    def pump3ChildDevice = fetchChild(false, "Pump", "Pump 3")
    if (pump3ChildDevice != null) {
        switch (decoded[15] & 48) { // Pump 3
            case 16:
            	pumpState[3] = "low"
                break
            case 32:
            	pumpState[3] = "high"
                break
            default:
            	pumpState[3] = "off"
        }
        pump3ChildDevice.parse(pumpState[3])
    }
    def pump4ChildDevice = fetchChild(false, "Pump", "Pump 4")
    if (pump4ChildDevice != null) {
        switch (decoded[15] & 192) {
            case 64:
            	pumpState[4] = "low"
                break
            case 128:
            	pumpState[4] = "high"
                break
            default:
            	pumpState[4] = "off"
        }
        pump4ChildDevice.parse(pumpState[4])
    }
    def pump5ChildDevice = fetchChild(false, "Pump", "Pump 5")
    if (pump5ChildDevice != null) {
        switch (decoded[16] & 3) {
            case 1:
            	pumpState[5] = "low"
                break
            case 2:
            	pumpState[5] = "high"
                break
            default:
            	pumpState[5] = "off"
        }
        pump5ChildDevice.parse(pumpState[5])
    }
    def pump6ChildDevice = fetchChild(false, "Pump", "Pump 6")
    if (pump6ChildDevice != null) {
        switch (decoded[16] & 12) {
            case 4:
            	pumpState[6] = "low"
                break
            case 8:
            	pumpState[6] = "high"
                break
            default:
            	pumpState[6] = "off"
        }
        pump6ChildDevice.parse(pumpState[6])
    }
    def pump7ChildDevice = fetchChild(false, "Pump", "Pump 7")
    if (pump7ChildDevice != null) {
        switch (decoded[16] & 48) {
            case 16:
            	pumpState[7] = "low"
                break
            case 32:
            	pumpState[7] = "high"
                break
            default:
            	pumpState[7] = "off"
        }
        pump7ChildDevice.parse(pumpState[7])
    }
    def pump8ChildDevice = fetchChild(false, "Pump", "Pump 8")
    if (pump8ChildDevice != null) {
        switch (decoded[16] & 192) {
            case 4:
            	pumpState[8] = "low"
                break
            case 8:
            	pumpState[8] = "high"
                break
            default:
            	pumpState[8] = "off"
        }
        pump8ChildDevice.parse(pumpState[8])
    }

    
    def blowerState = []
    blowerState[0] = null
    
    def blower1ChildDevice = fetchChild(false, "Blower", "Blower 1")
    if (blower1ChildDevice != null) {
        switch (decoded[17] & 12) {
            case 4:
                blowerState[1] = "low"
                break
            case 8:
                blowerState[1] = "medium"
                break
            case 12:
                blowerState[1] = "high"
                break
            default:
                blowerState[1] = "off"
        }
        blower1ChildDevice.parse(blowerState[1])
    }
    
    def blower2ChildDevice = fetchChild(false, "Blower", "Blower 2")
    if (blower2ChildDevice != null) {
        switch (decoded[17] & 48) {
            case 16:
                blowerState[2] = "low"
                break
            case 32:
                blowerState[2] = "medium"
                break
            case 48:
                blowerState[2] = "high"
                break
            default:
                blowerState[2] = "off"
        }
        blower2ChildDevice.parse(blowerState[2])
    }
    
    // Lights
    def lightState = []
    lightState[0] = null
    def light1ChildDevice = fetchChild(false, "Switch", "Light 1")
    if (light1ChildDevice != null) {
        lightState[1] = (decoded[18] & 3) != 0
        light1ChildDevice.parse(lightState[1])
    }
    def light2ChildDevice = fetchChild(false, "Switch", "Light 2")
    if (light2ChildDevice != null) {
        lightState[2] = (decoded[18] & 12) != 0
        light2ChildDevice.parse(lightState[2])
    } 
    def light3ChildDevice = fetchChild(false, "Switch", "Light 3")
    if (light3ChildDevice != null) {
        lightState[3] = (decoded[18] & 48) != 0
        light2ChildDevice.parse(lightState[3])
    } 
    def light4ChildDevice = fetchChild(false, "Switch", "Light 4")
    if (light4ChildDevice != null) {
        lightState[4] = (decoded[18] & 192) != 0
        light2ChildDevice.parse(lightState[4])
    }
    
    // Misters
    def misterState = []
    misterState[0] = null
    def mister1ChildDevice = fetchChild(false, "Switch", "Mister 1")
    if (mister1ChildDevice != null) {
        misterState[1] = (decoded[19] & 1) != 0
        mister1ChildDevice.parse(misterState[1])
    }    
    def mister2ChildDevice = fetchChild(false, "Switch", "Mister 2")
    if (mister2ChildDevice != null) {
        misterState[2] = (decoded[19] & 2) != 0
        mister2ChildDevice.parse(misterState[2])
    }    
    def mister3ChildDevice = fetchChild(false, "Switch", "Mister 3")
    if (mister3ChildDevice != null) {
        misterState[3] = (decoded[19] & 4) != 0
        mister3ChildDevice.parse(misterState[3])
    }
    
    // Aux
    def auxState = []
    auxState[0] = null
    def aux1ChildDevice = fetchChild(false, "Switch", "Aux 1")
    if (aux1ChildDevice != null) {
        auxState[1] = (decoded[19] & 8) != 0
        aux1ChildDevice.parse(auxState[1])
    }
    def aux2ChildDevice = fetchChild(false, "Switch", "Aux 2")
    if (aux2ChildDevice != null) {
        auxState[2] = (decoded[19] & 16) != 0
        aux2ChildDevice.parse(auxState[2])
    }
    def aux3ChildDevice = fetchChild(false, "Switch", "Aux 3")
    if (aux3ChildDevice != null) {
        auxState[3] = (decoded[19] & 32) != 0
        aux3ChildDevice.parse(auxState[3])
    }
    def aux4ChildDevice = fetchChild(false, "Switch", "Aux 4")
    if (aux4ChildDevice != null) {
        auxState[4] = (decoded[19] & 64) != 0
        aux4ChildDevice.parse(auxState[4])
    }
    
    def wifiState
    switch (decoded[16] & 240) {
    	case 0:
        	wifiState = "OK"
            break
        case 16:
        	wifiState = "Spa Not Communicating"
            break
        case 32:
        	wifiState = "Startup"
            break
        case 48:
        	wifiState = "Prime"
            break
        case 64:
        	wifiState = "Hold"
            break
        case 80:
        	wifiState = "Panel"
            break
    }
    
    def pumpStateStatus
    if (decoded[15] < 1 && decoded[16] < 1 && (decoded[17] & 3) < 1) {
    	pumpStateStatus = "Off"
    } else {
    	pumpStateStatus = isHeating ? "Low Heat" : "Low"
    }
    
    if (actualTemperature == 255) {
    	actualTemperature = device.currentValue("temperature") * (temperatureScale == "C" ? 2.0F : 1)
    }
    
    if (temperatureScale == "C") {
    	actualTemperature /= 2.0F
    	targetTemperature /= 2.0F
    }
    
    logMessage(2, "Actual Temperature: ${actualTemperature}\n"
                + "Current Time Hour: ${currentTimeHour}\n"
                + "Current Time Minute: ${currentTimeMinute}\n"
                + "Is 24-Hour Time: ${is24HourTime}\n"
                + "Temperature Scale: ${temperatureScale}\n"
                + "Target Temperature: ${targetTemperature}\n"
                + "Filter Mode: ${filterMode}\n"
                + "Accessibility Type: ${accessibilityType}\n"
                + "Heating Mode: ${heatingMode}\n"
                + "lightState[1]: ${lightState[1]}\n"
                + "lightState[2]: ${lightState[2]}\n"
                + "lightState[3]: ${lightState[3]}\n"
                + "lightState[4]: ${lightState[4]}\n"
                + "Heat Mode: ${heatMode}\n"
                + "Is Heating: ${isHeating}\n"
                + "pumpState[1]: ${pumpState[1]}\n"
                + "pumpState[2]: ${pumpState[2]}\n"
                + "pumpState[3]: ${pumpState[3]}\n"
                + "pumpState[4]: ${pumpState[4]}\n"
                + "pumpState[5]: ${pumpState[5]}\n"
                + "pumpState[6]: ${pumpState[6]}\n"
                + "pumpState[7]: ${pumpState[7]}\n"
                + "pumpState[8]: ${pumpState[8]}\n"
                + "blowerState[1]: ${blowerState[1]}\n"
                + "blowerState[2]: ${blowerState[2]}\n"
                + "misterState[1]: ${misterState[1]}\n"
                + "misterState[2]: ${misterState[2]}\n"
                + "misterState[3]: ${misterState[3]}\n"
                + "auxState[1]: ${auxState[1]}\n"
                + "auxState[2]: ${auxState[2]}\n"
                + "auxState[3]: ${auxState[3]}\n"
                + "auxState[4]: ${auxState[4]}\n"
                + "pumpStateStatus: ${pumpStateStatus}\n"
                + "wifiState: ${wifiState}\n"
    )
    
    sendEvent(name: "spaStatus", value: "${heatMode}\n${isHeating ? "heating to ${targetTemperature}°" : "not heating"}")
}

def fetchChild(createIfDoesntExist, String type, String name, Integer balboaApiButtonNumber = 0) {
    String thisId = device.id
    def childDeviceName = "${thisId}-${name}"
    logMessage(2, "childDeviceName: '${childDeviceName}")
    
    def cd = getChildDevice(childDeviceName)
    if (!cd && createIfDoesntExist) {
        def driverName = "${DEVICE_NAME_PREFIX} - ${type}"
                
        logMessage(3, "Adding Child Device. Driver: '${driverName}', Name: '${childDeviceName}'")
        cd = addChildDevice(NAMESPACE, driverName, childDeviceName, [name: "${device.displayName} - ${name}", isComponent: true])
        
        // Switches will need to know their respective Balboa API Button IDs
        if (type != "Thermostat" && balboaApiButtonNumber > 0) {
            cd.setBalboaAPIButtonNumber(balboaApiButtonNumber)
        }
    }
    return cd
}

void refresh() {
    parent.pollChildren()
}