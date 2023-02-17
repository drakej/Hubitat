/*
 *  Hubitat BWA Spa Manager
 *  -> App
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
 *  1.0.0       2020-01-31      Updated icons and bumped version to match DTH version
 *  1.0.1b      2020-09-17      Modified to now work with Hubitat
 *  1.1.0       2020-10-11      Major rewrite: Now downloads hot tub config, supports child devices, major refactoring, etc.
 *  2.0.0       2022-06-28      Added new controls to decode from configuration
 *  2.0.1       2023-02-17      Decreased level for logging that was a bit noisy
 *
 */

import groovy.transform.Field

@Field static int LOG_LEVEL = 3

@Field static String NAMESPACE = "drakej"

definition(
    name: "BWA Spa Manager",
    namespace: NAMESPACE,
    author: "Jonathan Drake",
    description: "Access and control your BWA Spa.",
    category: "Health & Wellness",
    iconUrl: "https://raw.githubusercontent.com/richardpowellus/HBBWASpaManager/master/images/hot-tub.png",
    iconX2Url: "https://raw.githubusercontent.com/richardpowellus/HBBWASpaManager/master/images/hot-tub.png",
    iconX3Url: "https://raw.githubusercontent.com/richardpowellus/HBBWASpaManager/master/images/hot-tub.png",
    singleInstance: false
) {
}

preferences {
    page(name: "mainPage")
    page(name: "authPage")
    page(name: "authResultPage")
    page(name: "confirmPage")
}

@Field static String BWGAPI_API_URL = "https://bwgapi.balboawater.com/"
@Field static String PARENT_DEVICE_NAME_PREFIX = "BWA SPA - Parent"

/* Spa Map Key Values
    "appId"
    "deviceId"
    "deviceNetworkId"
    "deviceDisplayName"
*/

def logMessage(level, message) {
    if (level >= LOG_LEVEL) {
        if (level < 3) {
            log.debug message
        } else {
            log.info message
        }
    }
}

def confirmPage() {
    def spaConfiguration = ParseDeviceConfigurationData(getDeviceConfiguration(state.spa["deviceId"]))
    logMessage(2, "confirmPage() spaConfiguration.dump(): ${spaConfiguration}")
    
    state.spaConfiguration = spaConfiguration

    def setupParameters = ParseSetupParametersData(getSetupParameters(state.spa["deviceId"]))

    log.info setupParameters
    
    
    dynamicPage(name: "confirmPage", uninstall: true, install: true) {
        section ("Name your BWA Spa Device") {
            input(name: "spaParentDeviceName", type: "text", title: "Spa Parent Device Name:", required: false, defaultValue: state.spa["deviceDisplayName"], description: state.spa["deviceDisplayName"])
        }
        section("Found the following devices attached to your hot tub") {
            spaConfiguration.each { k, v ->
                if (v == true) {
                    paragraph("    ${k}")
                }
            }
        }
    }
}

def mainPage() {
    // Get spa if we don't have it already
    if (state.spa == null && state.token?.trim()) {
        getSpa()
    }
            
    dynamicPage(name: "mainPage", nextPage: "confirmPage", uninstall: false, install: false) {
        if (state.spa) {
            section("Found the following Spa (you can change the device name in the next step):") {
                paragraph("${state.spa["deviceDisplayName"]}")
            }
            section("How frequently do you want to poll the BWA cloud for changes? (Use a lower number if you care about trying to capture and respond to \"change\" events as they happen)") {
                input(name: "pollingInterval", title: "Polling Interval (in Minutes)", type: "enum", required: true, multiple: false, defaultValue: 5, options: ["1", "5", "10", "15", "30"])
            }              
        }  
        section("BWA Authentication") {
            href("authPage", title: "Cloud Authorization", description: "${state.credentialStatus ? state.credentialStatus+"\n" : ""}Click to enter BWA credentials")
        }
        section ("Name this instance of ${app.name}") {
            label name: "name", title: "Assign a name", required: false, defaultValue: app.name, description: app.name, submitOnChange: true
        }
    }
}

def authPage() {
    dynamicPage(name: "authPage", nextPage: "authResultPage", uninstall: false, install: false) {
        section("BWA Credentials") {
            input("username", "username", title: "User ID", description: "BWA User ID", required: true)
            input("password", "password", title: "Password", description: "BWA Password", required: true)
        }
    }
}

def authResultPage() {
    logMessage(3, "Attempting login with specified credentials...")
    
    doLogin()
    logMessage(2, "authResultPage() state.loginResponse: ${state.loginResponse}")
    
    // Check if login was successful
    if (state.token == null) {
        logMessage(2, "authResultPage() state.token == null")
        dynamicPage(name: "authResultPage", nextPage: "authPage", uninstall: false, install: false) {
            section("${state.loginResponse}") {
                paragraph ("Please check your credentials and try again.")
            }
        }
    } else {
        logMessage(2, "authResultPage() state.token != null")
        dynamicPage(name: "authResultPage", nextPage: "mainPage", uninstall: false, install: false) {
            section("${state.loginResponse}") {
                paragraph ("Please click next to continue setting up your spa.")
            }
        }
    }
}

boolean doLogin(){
    def loggedIn = false
    def resp = doCallout("POST", "/users/login", [username: username, password: password])
    
    switch (resp.status) {
        case 403:
            state.loginResponse = "Access forbidden"
            state.credentialStatus = "[Disconnected]"
            state.token = null
            state.spa = null
            break
        case 401:
            state.loginResponse = resp.data.message
            state.credentialStatus = "[Disconnected]"
            state.token = null
            state.spa = null
            break
        case 200:
            logMessage(3, "Successfully logged in.")
            loggedIn = true
            state.loginResponse = "Logged in"
            state.token = resp.data.token
            state.credentialStatus = "[Connected]"
            state.loginDate = toStDateString(new Date())
            cacheSpaData(resp.data.device)
            logMessage(3, "Done caching SPA data.")
            break
        default:
            logMessage(2, resp.data)
            state.loginResponse = "Login unsuccessful"
            state.credentialStatus = "[Disconnected]"
            state.token = null
            state.spa = null
            break
    }

    logMessage(2, "loggedIn: ${loggedIn}, resp.status: ${resp.status}")
    return loggedIn
}

def reAuth() {
    if (!doLogin())
        doLogin() // timeout or other issue occurred, try one more time
}

def getSpa() {
    logMessage(3, "Getting Spa data from Balboa API...")
    def data = doCallout("POST", "/users/login", [username: username, password: password]).data
    return cacheSpaData(data.device)
}

def cacheSpaData(spaData) {
    // save in state so we can re-use in settings
    logMessage(3, "Saving Spa data in the state cache (app.id: ${app.id}, device_id: ${spaData.device_id})...")
    state.spa = [:]
    state.spa["appId"] = app.id;
    state.spa["deviceId"] = spaData.device_id
    state.spa["deviceNetworkId"] = [app.id, spaData.device_id].join('.')
    state.spa["deviceDisplayName"] = "Spa " + spaData.device_id[-8..-1]
    return spaData
}

def doCallout(calloutMethod, urlPath, calloutBody) {
    doCallout(calloutMethod, urlPath, calloutBody, "json", null)
}

def doCallout(calloutMethod, urlPath, calloutBody, contentType) {
    doCallout(calloutMethod, urlPath, calloutBody, contentType, null)
}

def doCallout(calloutMethod, urlPath, calloutBody, contentType, queryParams) {
    logMessage(2, "\"${calloutMethod}\"-ing ${contentType} to \"${urlPath}\"")
    def content_type
    switch(contentType) {
        case "xml":
            content_type = "application/xml"
            break
        case "json":
        default:
            content_type = "application/json"
            break
    }
    def params = [
        uri: BWGAPI_API_URL,
        path: "${urlPath}",
        query: queryParams,
        headers: [
            Authorization: state.token?.trim() ? "Bearer ${state.token as String}" : null
        ],
        requestContentType: content_type,
        body: calloutBody
    ]
    
    def result
    try {
        switch (calloutMethod) {
            case "GET":
                httpGet(params) { resp ->
                    result = resp
                }
                break
            case "PATCH":
                params.headers["x-http-method-override"] = "PATCH"
                // NOTE: break is purposefully missing so that it falls into the next case and "POST"s
            case "POST":
            	httpPost(params) { resp ->
                	result = resp
                }
                break
            default:
                log.error "unhandled method"
                return [error: "unhandled method"]
                break
        }
    } catch (groovyx.net.http.HttpResponseException e) {
    	log.debug e
        return e.response
    } catch (e) {
        log.error "Something went wrong: ${e}"
        return [error: e.message]
    }
    
    return result
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    // Not sure when tokens expire, but will get a new one every 24 hours just in case by scheduling to reauthorize every day
    if(state.loginDate?.trim()) schedule(parseStDate(state.loginDate), reAuth)

    def delete = getChildDevices().findAll { !state.spa["deviceNetworkId"] }
    delete.each {
        deleteChildDevice(it.deviceNetworkId)
    }
      
    def childDevices = []
    try {
        def childDevice = getChildDevice(state.spa["deviceId"])
        if(!childDevice) {
            logMessage(4, "Adding device: ${settings.spaParentDeviceName} [${state.spa["deviceId"]}]")
            childDevice = addChildDevice(NAMESPACE, PARENT_DEVICE_NAME_PREFIX, state.spa["deviceId"], [label: settings.spaParentDeviceName])
            state.spa["deviceDisplayName"] = settings.spaParentDeviceName
            childDevice.parseDeviceData(state.spa)
            childDevice.createChildDevices(state.spaConfiguration)
        }
        childDevices.add(childDevice)
    } catch (e) {
        log.error "Error creating device: ${e}"
    }
    
    // set up polling only if we have child devices
    if(childDevices.size() > 0) {
        pollChildren()
        "runEvery${pollingInterval}Minute${pollingInterval != "1" ? 's' : ''}"("pollChildren")
    } else unschedule(pollChildren)
}

def pollChildren() {
    logMessage(2, "polling...")
    def devices = getChildDevices()
    devices.each {
        def deviceId = it.currentValue("deviceId", true)
        if (deviceId == null) {
            logMessage(3, "Error, deviceId was null. Didn't actually poll the server. Retrying...")
            runIn(1, pollChildren)
            return
        }
        def deviceData = getPanelUpdate(deviceId)
        it.parsePanelData(deviceData)
    }
}

// Get device configuration
def getDeviceConfiguration(device_id) {
    logMessage(3, "Getting device configuration for ${device_id}")
    def resp = doCallout("POST", "/devices/sci", getXmlRequest(device_id, "DeviceConfiguration"), "xml")
    return resp.data
}

// Decode the encoded configuration data received from Balboa
def ParseDeviceConfigurationData(encodedData) {
    logMessage(2, "encodedData: '${encodedData}'")
    byte[] decoded = encodedData.decodeBase64()
    logMessage(2, "decoded: '${decoded}'")
    def returnValue = [:]

    returnValue["Pump0"] = (decoded[7] & 128) != 0 ? true : false

    returnValue["Pump1"] = (decoded[4] & 3) != 0 ? true : false
    returnValue["Pump2"] = (decoded[4] & 12) != 0 ? true : false
    returnValue["Pump3"] = (decoded[4] & 48) != 0 ? true : false
    returnValue["Pump4"] = (decoded[4] & 192) != 0 ? true : false
    
    returnValue["Pump5"] = (decoded[5] & 3) != 0 ? true : false
    returnValue["Pump6"] = (decoded[5] & 192) != 0 ? true : false
    
    returnValue["Pump7"] = (decoded[5] & 48) != 0 ? true : false
    returnValue["Pump8"] = (decoded[5] & 192) != 0 ? true : false   
    
    returnValue["Light1"] = (decoded[6] & 3) != 0 ? true : false
    returnValue["Light2"] = (decoded[6] & 192) != 0 ? true : false
    returnValue["Light3"] = (decoded[6] & 48) != 0 ? true : false
    returnValue["Light4"] = (decoded[6] & 192) != 0 ? true : false

    returnValue["Blower1"] = (decoded[7] & 3) != 0 ? true : false
    returnValue["Blower2"] = (decoded[7] & 12) != 0 ? true : false
    
    returnValue["Aux1"] = (decoded[8] & 1) != 0 ? true : false
    returnValue["Aux2"] = (decoded[8] & 2) != 0 ? true : false
    returnValue["Aux3"] = (decoded[8] & 4) != 0 ? true : false
    returnValue["Aux4"] = (decoded[8] & 8) != 0 ? true : false
    
    returnValue["Mister1"] = (decoded[8] & 16) != 0 ? true : false
    returnValue["Mister2"] = (decoded[8] & 32) != 0 ? true : false
    
    return returnValue
}

def ParseSetupParametersData(encodedData) {
    log.debug "encodedData: '${encodedData}'"

    byte[] decoded = encodedData.decodeBase64()

    log.debug "decoded: '${decoded}'"

    def result = [:]

    result["lowRangeMinTemp"] = decoded[6]
    result["lowRangeMaxTemp"] = decoded[7]
    result["highRangeMinTemp"] = decoded[8]
    result["highRangeMaxTemp"] = decoded[9]

    return result

}

// Get panel update
def getPanelUpdate(device_id) {
    logMessage(3, "Getting panel update for ${device_id}")
    def resp = doCallout("POST", "/devices/sci", getXmlRequest(device_id, "PanelUpdate"), "xml")
    return resp.data
}

def getSetupParameters(device_id) {
    def resp = doCallout("POST", "/devices/sci", getXmlRequest(device_id, "SetupParameters"), "xml")
    return resp.data
}

def getXmlRequest(deviceId, fileName) {
    return "<sci_request version=\"1.0\"><file_system cache=\"false\"><targets><device id=\"${deviceId}\"/></targets><commands><get_file path=\"${fileName}.txt\"/></commands></file_system></sci_request>"
}

def sendCommand(deviceId, targetName, data) {
    logMessage(3, "sending ${targetName}:${data} command for ${deviceId}")
    def resp = doCallout("POST", "/devices/sci", getXmlRequest(deviceId, targetName, data), "xml")
    return resp.data
}

def getXmlRequest(deviceId, targetName, data) {
    return "<sci_request version=\"1.0\"><data_service><targets><device id=\"${deviceId}\"/></targets><requests><device_request target_name=\"${targetName}\">${data}</device_request></requests></data_service></sci_request>"
}

def isoFormat() {
    return "yyyy-MM-dd'T'HH:mm:ss.SSSZ"
}

def toStDateString(date) {
    return date.format(isoFormat())
}

def parseStDate(dateStr) {
    return dateStr?.trim() ? timeToday(dateStr) : null
}