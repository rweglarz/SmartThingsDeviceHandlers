/**
 *  Neo Coolcam PIR
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
	definition (name: "Neo Coolcam PIR", namespace: "rafalweglarz", author: "Rafal Weglarz") {
		capability "Battery"
		capability "Illuminance Measurement"
		capability "Motion Sensor"
		capability "Refresh"
		capability "Relative Humidity Measurement"
		capability "Sensor"
		capability "Temperature Measurement"
	}
    command "refresh"
    command "refreshConfig"

	simulator {
		// TODO: define status and reply messages here
	}
	preferences {
		input "onOffDuration", "number", 
			title: "On-Off Duration (seconds)", 
			range: "5..600", 
			defaultValue: 30, 
			displayDuringSetup: true,
			required: false
		input "sensitivity", "number", 
			title: "Sensitivity", 
			range: "8..255", 
			defaultValue: 12, 
			displayDuringSetup: true,
			required: false
    }
	tiles(scale:2) {
		standardTile ("motion", "device.motion", width: 2, height: 2, canChangeIcon: true ) {
			state "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
			state "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
		}			
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
        	state("temp", label:'${currentValue}', unit:"dC", 
            		backgroundColors:[
            			[value: 15, color: "#42adf4"],
            			[value: 20, color: "#f4b841"]
                	]
            )
		}
		valueTile("illuminance", "device.illuminance", width: 2, height: 2) {
        	state("illuminance", label:'${currentValue} lux')
		}
 		valueTile("battery", "device.battery", width: 2, height: 2) {
        	state("battery", label:'${currentValue}% battery')
        }
        
        standardTile("refresh", "device.generic", width: 1, height: 1) {
			state "default", label:'refresh', action: "refresh", icon:"st.secondary.refresh-icon"
		}
        standardTile("refreshC", "device.generic", width: 1, height: 1) {
			state "default", label:'refConfig', action: "refreshConfig", icon:"st.secondary.refresh-icon"
		}
        main("temperature")

	}
}

// parse events into attributes
def parse(String description) {
//	log.debug "Parsing '${description}'"
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
def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv2.SensorBinaryReport cmd) {
	def ev
    if (cmd.sensorType==12) {
    	def motionVal = cmd.sensorValue ? "active" : "inactive"
//        ev = motionEvent(motionVal)
    } else {
		log.debug "SensorBinaryReport cmd: $cmd"
    }
    [ev]
}
def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
	def ev
	if (cmd.notificationType == 7 && cmd.event == 8) {
//		log.debug "notification type 7 event 8 triggered motion event"
		ev = motionEvent("active")
	} else if (cmd.notificationType == 7 && cmd.event == 0) {
//		log.debug "notification type 7 event 0 motion stopped"
		ev = motionEvent("inactive")
    } else {
		log.debug "NotificationReport cmd: $cmd"
    }
    [ev]
}
def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	def ev
    if (cmd.sensorType==1) {
		ev = createEvent("name":"temperature", "value":cmd.scaledSensorValue, "isStateChange":true)
    } else if (cmd.sensorType==3) {
		ev = createEvent("name":"illuminance", "value":cmd.scaledSensorValue, "isStateChange":true)
    } else {
    	log.debug "SensorMultilevelReport cmd: $cmd"
	}
	log.debug "SensorMultilevelReport ev: $ev"
    [ev]
}
def getPendingChanges() {
    def cmds = []
    if (state.pendingChanges==1) {
	    log.debug "func::getPendingChanges 1"
        state.pendingChanges = 0
        cmds+= configure()
     	cmds+= refresh()
    } else {
	    log.debug "func::getPendingChanges 2"
    }
    return response(cmds)
}
def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
	log.debug "ConfigurationReportv2 cmd: $cmd"
}
def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug "BatteryReport cmd: $cmd"
	sendEvent("name":"battery", "value":cmd.batteryLevel, "isStateChange":true)
}
def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) {
	log.debug "WakeUpNotificationv1 cmd: $cmd"
}	
def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd) {
	log.debug "WakeUpNotificationv2 cmd: $cmd"
    def event = createEvent(descriptionText: "${device.displayName} woke up", displayed: false)
    def cmds = []
    cmds+= getPendingChanges()
    cmds+= zwave.batteryV1.batteryGet().format()
    cmds+= zwave.wakeUpV1.wakeUpNoMoreInformation().format()
    cmds = delayBetween(cmds, 2000)
	[event, response(cmds)]
}
def configure() {	
	log.debug "configure"
    def sOnOff = settings?.onOffDuration.toInteger()
	log.debug "Setting onOff wait time to ${sOnOff} seconds"
    def sSens = settings?.sensitivity.toInteger()
	log.debug "Setting sensitivity to ${sSens}"
	def cmds = []
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 1, scaledConfigurationValue: sSens, size: 1).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 2, scaledConfigurationValue: sOnOff, size: 2).format()
    cmds << zwave.configurationV1.configurationSet(parameterNumber: 9, scaledConfigurationValue: 5, size: 1).format()
    return cmds
}
def updated() {
	log.debug "func::updated"
    state.pendingChanges = 1
}

def refresh() {
	log.debug "func::refresh"
	def cmds = []
	cmds << zwave.configurationV1.configurationGet(parameterNumber:1).format()
	cmds << zwave.configurationV1.configurationGet(parameterNumber:2).format()
    return cmds
}
def refreshConfig() {
	log.debug "func::refreshConfig"
    return configure()
}
private def motionEvent(motionVal) {
	def isStateChange = true
	if ( !state.motion?.trim() || state.motion==motionVal ) {
        isStateChange = false
    }
	log.debug "Motion stateChange:${isStateChange} prev:${state.motion} cmd:${motionVal}"
    state.motion = motionVal
	return sendEvent("name":"motion", "value":motionVal, "isStateChange":isStateChange)
}
private def secure(physicalgraph.zwave.Command cmd) {
	response(zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format())
}
