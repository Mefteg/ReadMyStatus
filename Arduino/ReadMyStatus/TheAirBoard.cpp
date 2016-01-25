/*******************************************************************************
This is The AirBoard utility library.

The AirBoard is a thumb-size, Arduino-compatible, wireless, low-power,
ubiquitous computer designed to sketch Internet-of-Things, fast!
Visit http://www.theairboard.cc
Upload your first sketch in seconds from https://codebender.cc?referral_code=Ub56L825Qb
Check README.txt and license.txt for more information.
All text above must be included in any redistribution.
*******************************************************************************/

#include "TheAirBoard.h"

/*******************************************************************************
 * Constructor : initialize utility library
 *******************************************************************************/
TheAirBoard::TheAirBoard(void) {
  digitalWrite(NCHG, HIGH);  // set internal pull-up for charge status
}

/*******************************************************************************
 * Power down processor
 * Don't forget to switch off the LED and the RF module for lowest consumption.
 * Board quiescent current budget:
 * charge circuit = 180 nA
 * ATmega328P deep sleep = 120 nA
 * regulators quiescent currents = 30 nA
 *******************************************************************************/
void TheAirBoard::powerDown() {
  byte br_high = UBRR0H, br_low = UBRR0L; // save baudrate register 
  Serial.end();
  digitalWrite(RX, LOW);           // reset internal pull-up serial link
  ADCSRA &= B01111111;             // disable ADC
  power_all_disable();
  /****************DON'T CHANGE************************************************/
  MCUCR = B01100000;               // BOD disable timed sequence
  MCUCR = B01000000;
  SMCR = B00000101;                // power down mode + sleep enable
  asm("sleep\n");
  sleep_disable();                 // first thing after wake up
  /****************************************************************************/
  power_all_enable();
  ADCSRA |= B10000000;             // enable ADC
  UBRR0H = br_high;                // set baud rate MSB
  UBRR0L = br_low;                 // set baud rate LSB
  UCSR0B |= (1<<RXCIE0)|(1<<UDRIE0)|(1<<RXEN0)|(1<<TXEN0); // enable uart
}

/*******************************************************************************
 * Set watchdog timeout to the nearest value among:
 * 0.016, 0.032, 0.064, 0.125, 0.25, 0.5, 1.0, 2.0, 4.0, 8.0 seconds
 *******************************************************************************/
void TheAirBoard::setWatchdog(int period) {
  byte wdp;
  wdp = round(log(period/16)/log(2));
  wdp = wdp & B00000111 | (wdp & B00001000)<<2;
  MCUSR &= ~(1<<WDRF);             // clear reset flag
  WDTCSR |= (1<<WDCE) | (1<<WDE);  // in order to change WDE or the prescaler, we need to set WDCE (This will allow updates for 4 clock cycles)
  WDTCSR = wdp;					   // set watchdog prescaler
  WDTCSR |= _BV(WDIE);             // enable the WD interrupt (note no reset)
}

/*******************************************************************************
 * Battery check utility
 * - check battery voltage, USB voltage and charge status
 * - blink red/green while charging
 * - solid green when discharging or when charged
 * - solid red when battery low
 * - return battery voltage indicator
 *******************************************************************************/
#define REFRESH    500 // battery check and status refresh period in ms
#define LOWBAT     3.1 // battery low voltage threshold in V
#define VF         1.2 // SML-P11VT forward voltage in V (under current adjusted)

float TheAirBoard::batteryChk(void) {
  static unsigned long time;
  static float vbat;
  boolean usbplug, batlow;
  
  if(millis() - time > REFRESH) {
    time = millis();

    // reset colors
    analogWrite(RED, LOW);
    analogWrite(GREEN, LOW);
    analogWrite(BLUE, LOW);

    int vusbValue = analogRead(VUSB);
    usbplug = vusbValue > 900; // No doc for this value, I just tested
    
    vbat = 3.3*analogRead(VBAT)/1024 + VF;
    batlow = vbat < LOWBAT;
    
    if(usbplug) { // USB plugged in
      if(digitalRead(NCHG)) { // charged
        analogWrite(RED, LOW);
        analogWrite(GREEN, HIGH);
        analogWrite(BLUE, LOW);
      }
      else { // charging
        analogWrite(RED, LOW);
        analogWrite(GREEN, LOW);
        analogWrite(BLUE, HIGH);
      }
    }
    else { // no USB
      if(batlow) { // battery low
        analogWrite(RED, HIGH);
        analogWrite(GREEN, LOW);
        analogWrite(BLUE, LOW);
      }
    }
  }
  
  return vbat;
}
			
