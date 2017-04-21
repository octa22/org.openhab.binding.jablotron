# org.openhab.binding.jablotron
Currently supported and tested JA-82 OASIS alarm with internet connection to Jablonet cloud (e.g. using JA-82Y GSM module)

#binding configuration
```
######################## Jablotron alarm ###########################

jablotron:email={email}
jablotron:password={password}
jablotron:armACode={1234}
jablotron:armBCode={2345}
jablotron:armABCCode={3456}
jablotron:disarmCode={9876}
```

#items file
```
String  HouseArm "Arm [%s]" <alarm>
Contact HouseAlarm "Alarm [%s]" <alarm> { jablotron="alarm" }
Switch	ArmSectionA	"Garage arming"	<jablotron>	(Alarm)	{ jablotron="A" }
Switch	ArmSectionAB	"1st floor arming"	<jablotron>	(Alarm)	{ jablotron="B" }
Switch	ArmSectionABC	"2nd floor arming"	<jablotron>	(Alarm)	{ jablotron="ABC" }
DateTime LastArmEvent "Last event [%1$td.%1$tm.%1$tY %1$tR]" <clock> { jablotron="lasteventtime" }
Switch	ArmControlPGX	"PGX"	<jablotron>	(Alarm)	{ jablotron="PGX" }
Switch	ArmControlPGY	"PGY"	<jablotron>	(Alarm)	{ jablotron="PGY" }
```

#sitemap example
```
Text item=HouseArm icon="alarm" {
    Switch item=ArmSectionA
    Switch item=ArmSectionAB
    Switch item=ArmSectionABC
    Text item=LastArmEvent
    Switch item=ArmControlPGX
    Switch item=ArmControlPGY
}
```

#rule example
```
rule "Arm"
when 
  Item ArmSectionA changed or Item ArmSectionAB changed or Item ArmSectionABC changed or 
  System started
then
   if( ArmSectionA.state.toString == "ON" || ArmSectionAB.state.toString == "ON" || ArmSectionABC.state.toString == "ON")
   {   postUpdate(HouseArm, "partial")  }
   if( ArmSectionA.state.toString == "OFF" && ArmSectionAB.state.toString == "OFF" && ArmSectionABC.state.toString == "OFF")
   {   postUpdate(HouseArm, "disarmed") }
   if( ArmSectionA.state.toString == "ON" && ArmSectionAB.state.toString == "ON" && ArmSectionABC.state.toString == "ON")
   {   postUpdate(HouseArm, "armed")    }
end
```