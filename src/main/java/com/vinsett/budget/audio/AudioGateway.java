
package com.vinsett.budget.audio;

public interface AudioGateway {
    String transcribe(AudioInput input);
    byte[] speak(String text);
}
