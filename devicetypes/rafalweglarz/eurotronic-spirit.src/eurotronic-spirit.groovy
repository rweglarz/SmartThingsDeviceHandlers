/**
 *  Eurotronic Spirit
 *
 *  Copyright 2018 Rafal Weglarz
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
 */
metadata {
	definition (name: "Eurotronic Spirit", namespace: "rafalweglarz", author: "Rafal Weglarz") {
		capability "Battery"
		capability "Configuration"
		capability "Temperature Measurement"
		capability "Thermostat Heating Setpoint"
		capability "Thermostat Mode"
		capability "Thermostat Operating State"
		capability "Thermostat Setpoint"
        capability "Refresh"
	}
	
    command "configure"
    command "refresh"
    command "refreshConfig"
    command "tempUp"
    command "tempDown"

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
        multiAttributeTile(name:"thermostatFull", type:"thermostat", width:6, height:4) {
		    tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
        		attributeState("temp", label:'${currentValue}', unit:"dC", defaultState: true)
    		}
    		tileAttribute("device.heatingSetpoint", key: "VALUE_CONTROL") {
        		attributeState("VALUE_UP", action: "tempUp")
        		attributeState("VALUE_DOWN", action: "tempDown")
    		}
        }
        valueTile("temperature", "device.temperature", width: 2, height: 2) {
        	state("temperature", label:'${currentValue}°',
            	backgroundColors:[
                                        [value: 19, color: "#153591"],
                                        [value: 44, color: "#1e9cbb"]
                                ]
             )
		}
        standardTile("refresh", "device.generic", width: 1, height: 1) {
			state "default", label:'Refresh', action: "refresh", icon:"st.secondary.refresh-icon"
		}
        standardTile("refresh config", "device.generic", width: 1, height: 1) {
			state "default", label:'RefreshConfig', action: "refreshConfig", icon:"st.secondary.refresh-icon"
		}

	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
    def result = null
    def cmd = zwave.parse(description)
    if (cmd) {
        result = zwaveEvent(cmd)
        log.debug "Parsed ${cmd} to ${result.inspect()}"
    } else {
        log.debug "Non-parsed event: ${description}"
    }
    return result
}
def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def result = []
	def encapCmd = cmd.encapsulatedCommand(commandClassVersions)
	if (encapCmd) {
	    log.debug "secure cmd: $encapCmd"
		result += zwaveEvent(encapCmd)
	}
	else {
		log.warn "Unable to extract encapsulated cmd from $cmd"	
	}
	return result
}
def zwaveEvent(physicalgraph.zwave.commands.thermostatsetpointv2.ThermostatSetpointReport cmd) {
	log.debug "ThermostatSetpointReport cmd: $cmd"
    state.heatingSetpoint = cmd.scaledValue
	sendEvent("name":"heatingSetpoint", "value":cmd.scaledValue, "isStateChange":true)
}
def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	log.debug "SensorMultilevelReportv5 cmd: $cmd"
	sendEvent("name":"temperature", "value":cmd.scaledSensorValue, "isStateChange":true)
}
def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	log.debug "SensorMultilevelReportv3 cmd: $cmd"
}
private getCommandClassVersions() {
	[
		0x20: 1,	// Basic
		0x30: 2,	// Sensor Binary
		0x31: 5,	// Sensor Multilevel
		0x56: 1,	// Crc16 Encap
		0x59: 1,  // AssociationGrpInfo
		0x5A: 1,  // DeviceResetLocally
		0x5E: 2,  // ZwaveplusInfo
		0x70: 2,  // Configuration
		0x71: 3,  // Notification v4
		0x72: 2,  // ManufacturerSpecific
		0x73: 1,  // Powerlevel
		0x80: 1,  // Battery
		0x84: 2,  // WakeUp
		0x85: 2,  // Association
		0x86: 1,	// Version (2)
		0x8E: 2,	// Multi Channel Association
		0x9C: 1,	// Sensor Alarm
		0x98: 1		// Security
	]
}

def tempChange(newTemp) {
	def commands = []
    state.scale = 0
    state.precision = 1
	commands << secure(zwave.thermostatSetpointV1.thermostatSetpointSet(setpointType: 1, scale: state.scale, precision: state.precision, scaledValue: newTemp))
	commands << secure(zwave.thermostatSetpointV1.thermostatSetpointGet(setpointType: 1))
    delayBetween(commands, standardDelay)
}
def tempUp() {
	log.debug "func:tempUp"
    return tempChange(state.heatingSetpoint+0.5)
}
def tempDown() {
	log.debug "func:tempDown"
    return tempChange(state.heatingSetpoint-0.5)
}
def refresh() {
	log.debug "func:refresh"
	return configure()
	def commands = []
	commands << secure(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1, scale:2).format())
	commands << secure(zwave.batteryV1.batteryGet().format())
	state.pendingRefresh = true
    delayBetween(commands, standardDelay)
//	return []
}
def refreshConfig() {
	log.debug "func:refreshConfig"
	state.refreshAll = true
	return []
}
def configure() {
	log.debug "Setting options"
    state.heatingSetpoint = 19
	delayBetween([
		//lcd timeout 0, 5-30
		secure(zwave.configurationV1.configurationSet(parameterNumber:30, size:1, scaledConfigurationValue:1)),
		//report temperature changes every 0.1-5.0 => 1-50
		secure(zwave.configurationV1.configurationSet(parameterNumber:5, size:1, scaledConfigurationValue:1)),
		//report valve change every 1%-100% => 1-10
		secure(zwave.configurationV1.configurationSet(parameterNumber:6, size:1, scaledConfigurationValue:2))
	])
}
def secure(physicalgraph.zwave.Command cmd) {
	response(zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format())
}
