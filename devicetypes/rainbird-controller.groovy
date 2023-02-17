/**
 * RainBird Irrigation Controller
 * Download: https://github.com/drakej/Hubitat/
 * Description: This is a device type driver for Hubitat. It communicates with a local Rain Bird API.
 * 
 *  Licensed under the The MIT License (MIT); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      https://mit-license.org/
 **/
preferences {
    input name: "apiHost",        type: "text",   title: "API Host", required: true
    input name: "logEnable",       type: "bool",   title: "Enable debug logging", defaultValue: true, required: true
}

metadata {
    definition (name:      "RainBird Irrigation", 
		namespace: "drakej", 
		author:    "Jonathan Drake", 
		importUrl: "https://raw.githubusercontent.com/drakej/Hubitat/master/devicetypes/rainbird-controller.groovy") {
		
        //capability "Switch"
        capability "Configuration"
        capability "Refresh"
        //capability "Valve"
        //capability "Actuator"

        command 'refresh'
		
        attribute "irrigating", "boolean"
    }
}


/**
 * Hubitat DTH Lifecycle Functions
 **/
def installed() {
    updated()
}

def updated(){
	log.debug "updated"
    
}

def refresh() {
	log.debug "refresh pressed"
    cleanState()
    queryControllerInfo()
}

/**
 * Event Parsers
 **/
def parse(String description) {
    def msg = parseLanMessage(description)
    if (msg.status == 200) {
        if (msg.json) {
            log.debug msg.json
            this.state.name = msg.json.Name
            this.state.serialNumber = msg.json.SerialNumber
            this.state.model = msg.json.Model
            this.state.version = msg.json.Version
        }
    } 
}

def networkIdForApp(String appId) {
    return "${device.deviceNetworkId}-${appId}"    
}

def appIdForNetworkId(String netId) {
    return netId.replaceAll(~/.*\-/,"")
}

private def cleanState() {
	def keys = this.state.keySet()
	for (def key : keys) {
		if (!isStateProperty(key)) {
			if (logEnable) log.debug("removing ${key}")
			this.state.remove(key)
		}
	}
}

/**
 * RainBird API Section
 **/

def queryControllerInfo() {
    def result = sendHubCommand(new hubitat.device.HubAction(
        method: "GET",
        path: "/controller",
        headers: [ HOST: "${apiHost}:8080" ]
    ))
    
    httpGet(uri: "http://${apiHost}:8080/irrigation/state") { response ->
        log.debug response.data
        sendEvent(name: "irrigating", value: response.data)
    }
}