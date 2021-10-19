/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.soundtrigger_middleware;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.hardware.soundtrigger.V2_1.ISoundTriggerHw;
import android.hardware.soundtrigger.V2_1.ISoundTriggerHwCallback;
import android.media.AudioFormat;
import android.media.MediaFormat;
import android.media.audio.common.AudioChannelLayout;
import android.media.audio.common.AudioConfig;
import android.media.audio.common.AudioConfigBase;
import android.media.audio.common.AudioFormatDescription;
import android.media.audio.common.AudioFormatType;
import android.media.soundtrigger.AudioCapabilities;
import android.media.soundtrigger.ConfidenceLevel;
import android.media.soundtrigger.Phrase;
import android.media.soundtrigger.PhraseRecognitionEvent;
import android.media.soundtrigger.PhraseRecognitionExtra;
import android.media.soundtrigger.PhraseSoundModel;
import android.media.soundtrigger.Properties;
import android.media.soundtrigger.RecognitionConfig;
import android.media.soundtrigger.RecognitionEvent;
import android.media.soundtrigger.RecognitionMode;
import android.media.soundtrigger.RecognitionStatus;
import android.media.soundtrigger.SoundModel;
import android.media.soundtrigger.SoundModelType;
import android.os.HidlMemoryUtil;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Test utilities, aimed at generating populated objects of the various types and validating
 * corresponding objects generated by the system under test.
 */
class TestUtil {
    private static final int AUDIO_FORMAT_MP3 = 0x01000000;  // matches native

    static SoundModel createGenericSoundModel() {
        return createSoundModel(SoundModelType.GENERIC);
    }

    private static SoundModel createSoundModel(@SoundModelType int type) {
        SoundModel model = new SoundModel();
        model.type = type;
        model.uuid = "12345678-2345-3456-4567-abcdef987654";
        model.vendorUuid = "87654321-5432-6543-7654-456789fedcba";
        byte[] data = new byte[]{91, 92, 93, 94, 95};
        model.data = byteArrayToParcelFileDescriptor(data);
        model.dataSize = data.length;
        return model;
    }

    private static void validateSoundModel_2_1(ISoundTriggerHw.SoundModel model, int type) {
        assertEquals(type, model.header.type);
        assertEquals("12345678-2345-3456-4567-abcdef987654",
                ConversionUtil.hidl2aidlUuid(model.header.uuid));
        assertEquals("87654321-5432-6543-7654-456789fedcba",
                ConversionUtil.hidl2aidlUuid(model.header.vendorUuid));
        assertArrayEquals(new byte[]{91, 92, 93, 94, 95},
                HidlMemoryUtil.hidlMemoryToByteArray(model.data));
    }

    private static void validateSoundModel_2_0(
            android.hardware.soundtrigger.V2_0.ISoundTriggerHw.SoundModel model, int type) {
        assertEquals(type, model.type);
        assertEquals("12345678-2345-3456-4567-abcdef987654",
                ConversionUtil.hidl2aidlUuid(model.uuid));
        assertEquals("87654321-5432-6543-7654-456789fedcba",
                ConversionUtil.hidl2aidlUuid(model.vendorUuid));
        assertArrayEquals(new Byte[]{91, 92, 93, 94, 95}, model.data.toArray());
    }

    static void validateGenericSoundModel_2_1(ISoundTriggerHw.SoundModel model) {
        validateSoundModel_2_1(model, android.hardware.soundtrigger.V2_0.SoundModelType.GENERIC);
    }

    static void validateGenericSoundModel_2_0(
            android.hardware.soundtrigger.V2_0.ISoundTriggerHw.SoundModel model) {
        validateSoundModel_2_0(model, android.hardware.soundtrigger.V2_0.SoundModelType.GENERIC);
    }

    static PhraseSoundModel createPhraseSoundModel() {
        PhraseSoundModel model = new PhraseSoundModel();
        model.common = createSoundModel(SoundModelType.KEYPHRASE);
        model.phrases = new Phrase[1];
        model.phrases[0] = new Phrase();
        model.phrases[0].id = 123;
        model.phrases[0].users = new int[]{5, 6, 7};
        model.phrases[0].locale = "locale";
        model.phrases[0].text = "text";
        model.phrases[0].recognitionModes =
                RecognitionMode.USER_AUTHENTICATION | RecognitionMode.USER_IDENTIFICATION;
        return model;
    }

    static void validatePhraseSoundModel_2_1(ISoundTriggerHw.PhraseSoundModel model) {
        validateSoundModel_2_1(model.common,
                android.hardware.soundtrigger.V2_0.SoundModelType.KEYPHRASE);
        validatePhrases_2_0(model.phrases);
    }

    static void validatePhraseSoundModel_2_0(
            android.hardware.soundtrigger.V2_0.ISoundTriggerHw.PhraseSoundModel model) {
        validateSoundModel_2_0(model.common,
                android.hardware.soundtrigger.V2_0.SoundModelType.KEYPHRASE);
        validatePhrases_2_0(model.phrases);
    }

    private static void validatePhrases_2_0(
            List<android.hardware.soundtrigger.V2_0.ISoundTriggerHw.Phrase> phrases) {
        assertEquals(1, phrases.size());
        assertEquals(123, phrases.get(0).id);
        assertArrayEquals(new Integer[]{5, 6, 7}, phrases.get(0).users.toArray());
        assertEquals("locale", phrases.get(0).locale);
        assertEquals("text", phrases.get(0).text);
        assertEquals(RecognitionMode.USER_AUTHENTICATION | RecognitionMode.USER_IDENTIFICATION,
                phrases.get(0).recognitionModes);
    }

    static android.hardware.soundtrigger.V2_0.ISoundTriggerHw.Properties createDefaultProperties_2_0(
            boolean supportConcurrentCapture) {
        android.hardware.soundtrigger.V2_0.ISoundTriggerHw.Properties properties =
                new android.hardware.soundtrigger.V2_0.ISoundTriggerHw.Properties();
        properties.implementor = "implementor";
        properties.description = "description";
        properties.version = 123;
        properties.uuid.timeLow = 1;
        properties.uuid.timeMid = 2;
        properties.uuid.versionAndTimeHigh = 3;
        properties.uuid.variantAndClockSeqHigh = 4;
        properties.uuid.node = new byte[]{5, 6, 7, 8, 9, 10};

        properties.maxSoundModels = 456;
        properties.maxKeyPhrases = 567;
        properties.maxUsers = 678;
        properties.recognitionModes =
                android.hardware.soundtrigger.V2_0.RecognitionMode.VOICE_TRIGGER
                        | android.hardware.soundtrigger.V2_0.RecognitionMode.USER_IDENTIFICATION
                        | android.hardware.soundtrigger.V2_0.RecognitionMode.USER_AUTHENTICATION
                        | android.hardware.soundtrigger.V2_0.RecognitionMode.GENERIC_TRIGGER;
        properties.captureTransition = true;
        properties.maxBufferMs = 321;
        properties.concurrentCapture = supportConcurrentCapture;
        properties.triggerInEvent = true;
        properties.powerConsumptionMw = 432;
        return properties;
    }

    static android.hardware.soundtrigger.V2_3.Properties createDefaultProperties_2_3(
            boolean supportConcurrentCapture) {
        android.hardware.soundtrigger.V2_3.Properties properties =
                new android.hardware.soundtrigger.V2_3.Properties();
        properties.base = createDefaultProperties_2_0(supportConcurrentCapture);
        properties.supportedModelArch = "supportedModelArch";
        properties.audioCapabilities =
                android.hardware.soundtrigger.V2_3.AudioCapabilities.ECHO_CANCELLATION
                        | android.hardware.soundtrigger.V2_3.AudioCapabilities.NOISE_SUPPRESSION;
        return properties;
    }

    static Properties createDefaultProperties(boolean supportConcurrentCapture) {
        Properties properties = new Properties();
        properties.implementor = "implementor";
        properties.description = "description";
        properties.version = 123;
        properties.uuid = "00000001-0002-0003-0004-05060708090a";
        properties.maxSoundModels = 456;
        properties.maxKeyPhrases = 567;
        properties.maxUsers = 678;
        properties.recognitionModes =
                RecognitionMode.VOICE_TRIGGER
                        | RecognitionMode.USER_IDENTIFICATION
                        | RecognitionMode.USER_AUTHENTICATION
                        | RecognitionMode.GENERIC_TRIGGER;
        properties.captureTransition = true;
        properties.maxBufferMs = 321;
        properties.concurrentCapture = supportConcurrentCapture;
        properties.triggerInEvent = true;
        properties.powerConsumptionMw = 432;
        properties.supportedModelArch = "supportedModelArch";
        properties.audioCapabilities = AudioCapabilities.ECHO_CANCELLATION
                | AudioCapabilities.NOISE_SUPPRESSION;
        return properties;
    }

    static void validateDefaultProperties(Properties properties,
            boolean supportConcurrentCapture) {
        validateDefaultProperties(properties, supportConcurrentCapture,
                AudioCapabilities.ECHO_CANCELLATION | AudioCapabilities.NOISE_SUPPRESSION,
                "supportedModelArch");
    }

    static void validateDefaultProperties(Properties properties,
            boolean supportConcurrentCapture, @AudioCapabilities int audioCapabilities,
            @NonNull String supportedModelArch) {
        assertEquals("implementor", properties.implementor);
        assertEquals("description", properties.description);
        assertEquals(123, properties.version);
        assertEquals("00000001-0002-0003-0004-05060708090a", properties.uuid);
        assertEquals(456, properties.maxSoundModels);
        assertEquals(567, properties.maxKeyPhrases);
        assertEquals(678, properties.maxUsers);
        assertEquals(RecognitionMode.GENERIC_TRIGGER
                | RecognitionMode.USER_AUTHENTICATION
                | RecognitionMode.USER_IDENTIFICATION
                | RecognitionMode.VOICE_TRIGGER, properties.recognitionModes);
        assertTrue(properties.captureTransition);
        assertEquals(321, properties.maxBufferMs);
        assertEquals(supportConcurrentCapture, properties.concurrentCapture);
        assertTrue(properties.triggerInEvent);
        assertEquals(432, properties.powerConsumptionMw);
        assertEquals(supportedModelArch, properties.supportedModelArch);
        assertEquals(audioCapabilities, properties.audioCapabilities);
    }

    static RecognitionConfig createRecognitionConfig() {
        RecognitionConfig config = new RecognitionConfig();
        config.captureRequested = true;
        config.phraseRecognitionExtras = new PhraseRecognitionExtra[]{new PhraseRecognitionExtra()};
        config.phraseRecognitionExtras[0].id = 123;
        config.phraseRecognitionExtras[0].confidenceLevel = 4;
        config.phraseRecognitionExtras[0].recognitionModes = 5;
        config.phraseRecognitionExtras[0].levels = new ConfidenceLevel[]{new ConfidenceLevel()};
        config.phraseRecognitionExtras[0].levels[0].userId = 234;
        config.phraseRecognitionExtras[0].levels[0].levelPercent = 34;
        config.data = new byte[]{5, 4, 3, 2, 1};
        config.audioCapabilities = AudioCapabilities.ECHO_CANCELLATION
                | AudioCapabilities.NOISE_SUPPRESSION;
        return config;
    }

    static void validateRecognitionConfig_2_0(
            android.hardware.soundtrigger.V2_0.ISoundTriggerHw.RecognitionConfig config,
            int captureDevice, int captureHandle) {
        assertTrue(config.captureRequested);
        assertEquals(captureDevice, config.captureDevice);
        assertEquals(captureHandle, config.captureHandle);
        assertEquals(1, config.phrases.size());
        android.hardware.soundtrigger.V2_0.PhraseRecognitionExtra halPhraseExtra =
                config.phrases.get(0);
        assertEquals(123, halPhraseExtra.id);
        assertEquals(4, halPhraseExtra.confidenceLevel);
        assertEquals(5, halPhraseExtra.recognitionModes);
        assertEquals(1, halPhraseExtra.levels.size());
        android.hardware.soundtrigger.V2_0.ConfidenceLevel halLevel = halPhraseExtra.levels.get(0);
        assertEquals(234, halLevel.userId);
        assertEquals(34, halLevel.levelPercent);
        assertArrayEquals(new Byte[]{5, 4, 3, 2, 1}, config.data.toArray());
    }

    static void validateRecognitionConfig_2_1(
            android.hardware.soundtrigger.V2_1.ISoundTriggerHw.RecognitionConfig config,
            int captureDevice, int captureHandle) {
        assertTrue(config.header.captureRequested);
        assertEquals(captureDevice, config.header.captureDevice);
        assertEquals(captureHandle, config.header.captureHandle);
        assertEquals(1, config.header.phrases.size());
        android.hardware.soundtrigger.V2_0.PhraseRecognitionExtra halPhraseExtra =
                config.header.phrases.get(0);
        assertEquals(123, halPhraseExtra.id);
        assertEquals(4, halPhraseExtra.confidenceLevel);
        assertEquals(5, halPhraseExtra.recognitionModes);
        assertEquals(1, halPhraseExtra.levels.size());
        android.hardware.soundtrigger.V2_0.ConfidenceLevel halLevel = halPhraseExtra.levels.get(0);
        assertEquals(234, halLevel.userId);
        assertEquals(34, halLevel.levelPercent);
        assertArrayEquals(new byte[]{5, 4, 3, 2, 1},
                HidlMemoryUtil.hidlMemoryToByteArray(config.data));
    }

    static void validateRecognitionConfig_2_3(
            android.hardware.soundtrigger.V2_3.RecognitionConfig config, int captureDevice,
            int captureHandle) {
        validateRecognitionConfig_2_1(config.base, captureDevice, captureHandle);

        assertEquals(AudioCapabilities.ECHO_CANCELLATION
                | AudioCapabilities.NOISE_SUPPRESSION, config.audioCapabilities);
    }

    static android.hardware.soundtrigger.V2_0.ISoundTriggerHwCallback.RecognitionEvent createRecognitionEvent_2_0(
            int hwHandle,
            int status) {
        android.hardware.soundtrigger.V2_0.ISoundTriggerHwCallback.RecognitionEvent halEvent =
                new android.hardware.soundtrigger.V2_0.ISoundTriggerHwCallback.RecognitionEvent();
        halEvent.status = status;
        halEvent.type = SoundModelType.GENERIC;
        halEvent.model = hwHandle;
        halEvent.captureAvailable = true;
        // This field is ignored.
        halEvent.captureSession = 9999;
        halEvent.captureDelayMs = 234;
        halEvent.capturePreambleMs = 345;
        halEvent.triggerInData = true;
        halEvent.audioConfig.sampleRateHz = 456;
        halEvent.audioConfig.channelMask = AudioFormat.CHANNEL_IN_MONO;  // matches native
        halEvent.audioConfig.format = AUDIO_FORMAT_MP3;
        // hwEvent.audioConfig.offloadInfo is irrelevant.
        halEvent.data.add((byte) 31);
        halEvent.data.add((byte) 32);
        halEvent.data.add((byte) 33);
        return halEvent;
    }

    static AudioFormatDescription createAudioFormatMp3() {
        AudioFormatDescription format = new AudioFormatDescription();
        format.type = AudioFormatType.NON_PCM;
        format.encoding = MediaFormat.MIMETYPE_AUDIO_MPEG;  // MP3
        return format;
    }

    static RecognitionEvent createRecognitionEvent(@RecognitionStatus int status,
            boolean recognitionStillActive) {
        RecognitionEvent event = new RecognitionEvent();
        event.status = status;
        event.type = SoundModelType.GENERIC;
        event.captureAvailable = true;
        event.captureDelayMs = 234;
        event.capturePreambleMs = 345;
        event.triggerInData = true;
        event.audioConfig = new AudioConfig();
        event.audioConfig.base = new AudioConfigBase();
        event.audioConfig.base.sampleRate = 456;
        event.audioConfig.base.channelMask = AudioChannelLayout.layoutMask(
                AudioChannelLayout.LAYOUT_MONO);
        event.audioConfig.base.format = createAudioFormatMp3();
        //event.audioConfig.offloadInfo is irrelevant.
        event.data = new byte[]{31, 32, 33};
        event.recognitionStillActive = recognitionStillActive;
        return event;
    }

    static ISoundTriggerHwCallback.RecognitionEvent createRecognitionEvent_2_1(
            int hwHandle,
            int status) {
        ISoundTriggerHwCallback.RecognitionEvent halEvent =
                new ISoundTriggerHwCallback.RecognitionEvent();
        halEvent.header = createRecognitionEvent_2_0(hwHandle, status);
        halEvent.header.data.clear();
        halEvent.data = HidlMemoryUtil.byteArrayToHidlMemory(new byte[]{31, 32, 33});
        return halEvent;
    }

    static void validateRecognitionEvent(RecognitionEvent event, @RecognitionStatus int status,
            boolean recognitionStillActive) {
        assertEquals(status, event.status);
        assertEquals(SoundModelType.GENERIC, event.type);
        assertTrue(event.captureAvailable);
        assertEquals(234, event.captureDelayMs);
        assertEquals(345, event.capturePreambleMs);
        assertTrue(event.triggerInData);
        assertEquals(456, event.audioConfig.base.sampleRate);
        assertEquals(AudioChannelLayout.layoutMask(AudioChannelLayout.LAYOUT_MONO),
                event.audioConfig.base.channelMask);
        assertEquals(createAudioFormatMp3(), event.audioConfig.base.format);
        assertArrayEquals(new byte[]{31, 32, 33}, event.data);
        assertEquals(recognitionStillActive, event.recognitionStillActive);
    }

    static PhraseRecognitionEvent createPhraseRecognitionEvent(@RecognitionStatus int status,
            boolean recognitionStillActive) {
        PhraseRecognitionEvent event = new PhraseRecognitionEvent();
        event.common = createRecognitionEvent(status, recognitionStillActive);

        PhraseRecognitionExtra extra = new PhraseRecognitionExtra();
        extra.id = 123;
        extra.confidenceLevel = 52;
        extra.recognitionModes = RecognitionMode.VOICE_TRIGGER
                | RecognitionMode.GENERIC_TRIGGER;
        ConfidenceLevel level = new ConfidenceLevel();
        level.userId = 31;
        level.levelPercent = 43;
        extra.levels = new ConfidenceLevel[]{level};
        event.phraseExtras = new PhraseRecognitionExtra[]{extra};
        return event;
    }

    static android.hardware.soundtrigger.V2_0.ISoundTriggerHwCallback.PhraseRecognitionEvent
    createPhraseRecognitionEvent_2_0(int hwHandle, int status) {
        android.hardware.soundtrigger.V2_0.ISoundTriggerHwCallback.PhraseRecognitionEvent halEvent =
                new android.hardware.soundtrigger.V2_0.ISoundTriggerHwCallback.PhraseRecognitionEvent();
        halEvent.common = createRecognitionEvent_2_0(hwHandle, status);

        android.hardware.soundtrigger.V2_0.PhraseRecognitionExtra halExtra =
                new android.hardware.soundtrigger.V2_0.PhraseRecognitionExtra();
        halExtra.id = 123;
        halExtra.confidenceLevel = 52;
        halExtra.recognitionModes = android.hardware.soundtrigger.V2_0.RecognitionMode.VOICE_TRIGGER
                | android.hardware.soundtrigger.V2_0.RecognitionMode.GENERIC_TRIGGER;
        android.hardware.soundtrigger.V2_0.ConfidenceLevel halLevel =
                new android.hardware.soundtrigger.V2_0.ConfidenceLevel();
        halLevel.userId = 31;
        halLevel.levelPercent = 43;
        halExtra.levels.add(halLevel);
        halEvent.phraseExtras.add(halExtra);
        return halEvent;
    }

    static ISoundTriggerHwCallback.PhraseRecognitionEvent createPhraseRecognitionEvent_2_1(
            int hwHandle, int status) {
        ISoundTriggerHwCallback.PhraseRecognitionEvent halEvent =
                new ISoundTriggerHwCallback.PhraseRecognitionEvent();
        halEvent.common = createRecognitionEvent_2_1(hwHandle, status);

        android.hardware.soundtrigger.V2_0.PhraseRecognitionExtra halExtra =
                new android.hardware.soundtrigger.V2_0.PhraseRecognitionExtra();
        halExtra.id = 123;
        halExtra.confidenceLevel = 52;
        halExtra.recognitionModes = android.hardware.soundtrigger.V2_0.RecognitionMode.VOICE_TRIGGER
                | android.hardware.soundtrigger.V2_0.RecognitionMode.GENERIC_TRIGGER;
        android.hardware.soundtrigger.V2_0.ConfidenceLevel halLevel =
                new android.hardware.soundtrigger.V2_0.ConfidenceLevel();
        halLevel.userId = 31;
        halLevel.levelPercent = 43;
        halExtra.levels.add(halLevel);
        halEvent.phraseExtras.add(halExtra);
        return halEvent;
    }

    static void validatePhraseRecognitionEvent(PhraseRecognitionEvent event,
            @RecognitionStatus int status, boolean recognitionStillActive) {
        validateRecognitionEvent(event.common, status, recognitionStillActive);

        assertEquals(1, event.phraseExtras.length);
        assertEquals(123, event.phraseExtras[0].id);
        assertEquals(52, event.phraseExtras[0].confidenceLevel);
        assertEquals(RecognitionMode.VOICE_TRIGGER | RecognitionMode.GENERIC_TRIGGER,
                event.phraseExtras[0].recognitionModes);
        assertEquals(1, event.phraseExtras[0].levels.length);
        assertEquals(31, event.phraseExtras[0].levels[0].userId);
        assertEquals(43, event.phraseExtras[0].levels[0].levelPercent);
    }

    private static ParcelFileDescriptor byteArrayToParcelFileDescriptor(byte[] data) {
        try (SharedMemory shmem = SharedMemory.create("", data.length)) {
            ByteBuffer buffer = shmem.mapReadWrite();
            buffer.put(data);
            return shmem.getFdDup();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
