/**
 *  Idle Shut Off
 *
 *  Copyright 2018-2023 Jonathan Drake
 *
 *  Licensed under the The MIT License (MIT); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      https://mit-license.org/
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Idle Shut Off",
    namespace: "drakej",
    author: "Jonathan Drake",
    description: "This application will shut off a device or switch after having been on for a given period of time.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("When this switch turns on") {
		// TODO: put inputs here
        input "switch1", "capability.switch", required: true
	}
    section("Turn off after it's been on for") {
    	input "minutes", "number", required: true, title: "Minutes?"
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    subscribe(switch1, "switch.on", switchOnHandler)
}

def switchOnHandler(event) {
	log.debug "Switch was turned on"
    runIn(60*minutes, turnOffHandler)
}

def turnOffHandler() {
    log.debug "Switch was turned off"
	switch1.off()
}