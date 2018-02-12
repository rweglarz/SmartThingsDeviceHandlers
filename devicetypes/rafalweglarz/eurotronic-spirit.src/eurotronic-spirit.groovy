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
		capability "Polling"
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
    command "setHeatingSetpoint"
    command "tempUp"
    command "tempDown"

	simulator {
		// TODO: define status and reply messages here
	}
	preferences {
		input "tempOffset", "number", 
			title: "temp offset -5<>5", 
			range: "-5..5", 
			defaultValue: 0, 
			displayDuringSetup: true,
			required: false
    }
	tiles(scale: 2) {
        multiAttributeTile(name:"thermostatFull", type:"thermostat", width:6, height:4) {
		    tileAttribute("device.temperature", key: "PRIMARY_CONTROL") {
        		attributeState("temp", label:'${currentValue}', unit:"dC", 
                	backgroundColors:[
            			[value: 15, color: "#42adf4"],
            			[value: 20, color: "#f4b841"]
                	])
    		}
    		tileAttribute("device.heatingSetpoint", key: "VALUE_CONTROL") {
        		attributeState("VALUE_UP", action: "tempUp")
        		attributeState("VALUE_DOWN", action: "tempDown")
    		}
        }
        valueTile("battery", "device.battery", width: 2, height: 2) {
        	state("battery", label:'${currentValue}% battery')
        }
        standardTile("refresh", "device.generic", width: 1, height: 1) {
			state "default", label:'Refresh', action: "refresh", icon:"st.secondary.refresh-icon"
		}
        standardTile("refreshC", "device.generic", width: 1, height: 1) {
			state "default", label:'refConfig', action: "refreshConfig", icon:"st.secondary.refresh-icon"
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
//        log.debug "Parsed ${cmd} to ${result.inspect()}"
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
	} else {
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
	sendEvent("name":"level", "value":cmd.value, "isStateChange":false)
}
def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
	log.debug "ConfigurationReportv2 cmd: $cmd"
}
def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug "batteryv1 cmd: $cmd"
	sendEvent("name":"battery", "value":cmd.batteryLevel, "isStateChange":true)
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
	log.debug "func:tempChange ${newTemp}"
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
def setHeatingSetpoint(newTemp) {
	log.debug "func:setHeatingSetpoint"
    return tempChange(newTemp)
}
def poll() {
	def commands = []
	commands << secure(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1, scale:2))
    delayBetween(commands, standardDelay)
}
def refresh() {
	log.debug "func:refresh"
	def commands = []
	commands << secure(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1, scale:2))
	commands << secure(zwave.configurationV1.configurationGet(parameterNumber:8))
	commands << secure(zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType:1, scale:2))
	commands << secure(zwave.batteryV1.batteryGet())
	state.pendingRefresh = true
	state.refreshAll = true
    delayBetween(commands, 2500)
}
def refreshConfig() {
	log.debug "func:refreshConfig"
	return configure()
}
def configure() {
	log.debug "Setting options"
    if (! state.heatingSetpoint) {
    	log.debug "Setting initial heating point"
    	state.heatingSetpoint = 19
    }
	def tempOffsetI = settings.tempOffset.toInteger()*10
	log.debug("Settings::Temperature offset: ${settings.tempOffset}")

	delayBetween([
		//report temperature changes every 0.1-5.0 => 1-50
		secure(zwave.configurationV1.configurationSet(parameterNumber:5, size:1, scaledConfigurationValue:2)),
		//report valve change every 1%-100% => 0-100
		secure(zwave.configurationV1.configurationSet(parameterNumber:6, size:1, scaledConfigurationValue:0)),
		//temperature offset -5 - 5 => -50-50
        secure(zwave.configurationV1.configurationSet(parameterNumber:8, size:1, scaledConfigurationValue:tempOffsetI)),
		//lcd timeout 0, 5-30
		secure(zwave.configurationV1.configurationSet(parameterNumber:2, size:1, scaledConfigurationValue:0))
	])
}
def updated() {
	log.debug "updated"
	configure()
    refresh()
}
def secure(physicalgraph.zwave.Command cmd) {
	response(zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format())
}
