/*
Ask for my tablet status periodically.
*/

#include "TheAirBoard.h"

#define BAUD 115200

#define MESSAGE_TYPE_UPDATE "UPDATE\n"
#define MESSAGE_TYPE_SUCCESS "SUCCESS\n"
#define MESSAGE_TYPE_ERROR "ERROR\n"

#define TAG_BATTERY_SEPARATOR '$'
#define TAG_DISK_SEPARATOR '&'

#define DELAY_BEFORE_UPDATE 5000
#define DELAY_AFTER_UPDATE 1000

#define PIN_BATTERY 10

TheAirBoard board;

float battery = 0.0f;

// the setup routine runs once when you press reset:
void setup()
{
  // initial baud	
	Serial.begin(BAUD);

  analogWrite(RED, LOW);
  analogWrite(GREEN, LOW);
  analogWrite(BLUE, LOW);

  pinMode(PIN_BATTERY, OUTPUT);
}

// the loop routine runs over and over again forever:
void loop()
{
  // check the battery
  board.batteryChk();

  analogWrite(PIN_BATTERY, 255.0f * battery);
  
	// if something is available to read
	if(Serial.available())
    {
    	// read the message
    	String data = Serial.readString();
    	
    	// prepare data
    	String dataBattery;
    	String dataDisk;
    	
    	// parse the battery data
    	int indexBatterySeparator = data.indexOf(TAG_BATTERY_SEPARATOR);
    	if (indexBatterySeparator > -1)
    	{
    		dataBattery = data.substring(0, indexBatterySeparator);
    		
    		// parse the disk data
    		int indexDiskSeparator = data.indexOf(TAG_DISK_SEPARATOR, indexBatterySeparator);
	    	if (indexDiskSeparator > -1)
	    	{
	    		dataDisk = data.substring(indexBatterySeparator, indexDiskSeparator);
	    	} 
    	}

		// if all data are OK
    	if (dataBattery.length() > 0 && dataDisk.length() > 0)
    	{
    		Serial.write(MESSAGE_TYPE_SUCCESS);
    		delay(DELAY_AFTER_UPDATE);
    	}
    	else
    	{
    		Serial.write(MESSAGE_TYPE_ERROR);
    		analogWrite(RED, HIGH);
    		delay(DELAY_AFTER_UPDATE);
    		analogWrite(RED, LOW);
    	}
    }
    
    // ask for an update
    Serial.write(MESSAGE_TYPE_UPDATE);
    
    // wait a bit
    delay(DELAY_BEFORE_UPDATE);
}
