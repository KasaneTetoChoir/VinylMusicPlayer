package com.poupa.vinylmusicplayer.discog.tagging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/**
 * Unit tests for ReplayGainTagExtractor parsing logic.
 */
@RunWith(JUnit4.class)
public class ReplayGainTagExtractorTest {

    private static final float DELTA = 0.0001f;

    @Test
    public void testParseFloat_variousFormats() {
        // 经典带 " dB" 后缀负增益
        assertEquals(-6.05f, ReplayGainTagExtractor.parseFloat("-6.05 dB"), DELTA);
        assertEquals(-11.98f, ReplayGainTagExtractor.parseFloat("-11.98 dB"), DELTA);

        // 正增益 (带 + 号)
        assertEquals(1.92f, ReplayGainTagExtractor.parseFloat("+1.92 dB"), DELTA);
        assertEquals(0.30f, ReplayGainTagExtractor.parseFloat("+0.30 dB"), DELTA);

        // 纯数字增益
        assertEquals(-5.38f, ReplayGainTagExtractor.parseFloat("-5.38"), DELTA);
        assertEquals(1.5f, ReplayGainTagExtractor.parseFloat("1.5"), DELTA);

        // Peak 峰值浮点数字符串
        assertEquals(0.99136f, ReplayGainTagExtractor.parseFloat("0.991360"), DELTA);
        assertEquals(1.070143f, ReplayGainTagExtractor.parseFloat("1.070143"), DELTA);

        // 异常/空值保护
        assertEquals(0.0f, ReplayGainTagExtractor.parseFloat(""), DELTA);
        assertEquals(0.0f, ReplayGainTagExtractor.parseFloat(null), DELTA);
        assertEquals(0.0f, ReplayGainTagExtractor.parseFloat("invalid_text"), DELTA);
        assertEquals(0.0f, ReplayGainTagExtractor.parseFloat("0 dB"), DELTA);
    }

    @Test
    public void testParseTags_VorbisCommentTag() throws Exception {
        VorbisCommentTag tag = new VorbisCommentTag();
        tag.setField("REPLAYGAIN_TRACK_GAIN", "-11.98 dB");
        tag.setField("REPLAYGAIN_TRACK_PEAK", "1.070143");
        tag.setField("REPLAYGAIN_ALBUM_GAIN", "-12.56 dB");
        tag.setField("REPLAYGAIN_ALBUM_PEAK", "1.076470");

        Map<String, Float> result = ReplayGainTagExtractor.parseTags(tag);
        assertNotNull(result);
        assertEquals(-11.98f, result.get("REPLAYGAIN_TRACK_GAIN"), DELTA);
        assertEquals(1.070143f, result.get("REPLAYGAIN_TRACK_PEAK"), DELTA);
        assertEquals(-12.56f, result.get("REPLAYGAIN_ALBUM_GAIN"), DELTA);
        assertEquals(1.076470f, result.get("REPLAYGAIN_ALBUM_PEAK"), DELTA);
    }

    @Test
    public void testParseTags_FlacTag() throws Exception {
        FlacTag tag = new FlacTag();
        tag.setField("REPLAYGAIN_TRACK_GAIN", "+1.92 dB");
        tag.setField("REPLAYGAIN_TRACK_PEAK", "0.453084");

        Map<String, Float> result = ReplayGainTagExtractor.parseTags(tag);
        assertNotNull(result);
        assertEquals(1.92f, result.get("REPLAYGAIN_TRACK_GAIN"), DELTA);
        assertEquals(0.453084f, result.get("REPLAYGAIN_TRACK_PEAK"), DELTA);
        // 未设置 Album 字段时不应抛异常
        assertTrue(!result.containsKey("REPLAYGAIN_ALBUM_GAIN"));
    }

    @Test
    public void testParseMp4Tags_iTunesPrefix() throws Exception {
        Mp4Tag tag = new Mp4Tag();
        tag.addField(new Mp4TagReverseDnsField("----:com.apple.iTunes:REPLAYGAIN_TRACK_GAIN", "com.apple.iTunes", "REPLAYGAIN_TRACK_GAIN", "+0.30 dB"));
        tag.addField(new Mp4TagReverseDnsField("----:com.apple.iTunes:REPLAYGAIN_TRACK_PEAK", "com.apple.iTunes", "REPLAYGAIN_TRACK_PEAK", "1.021449"));

        Map<String, Float> result = ReplayGainTagExtractor.parseMp4Tags(tag);
        assertNotNull(result);
        assertEquals(0.30f, result.get("REPLAYGAIN_TRACK_GAIN"), DELTA);
        assertEquals(1.021449f, result.get("REPLAYGAIN_TRACK_PEAK"), DELTA);
    }

    @Test
    public void testParseMp4Tags_lowercaseITunesPrefix() throws Exception {
        Mp4Tag tag = new Mp4Tag();
        tag.addField(new Mp4TagReverseDnsField("----:com.apple.iTunes:replaygain_track_gain", "com.apple.iTunes", "replaygain_track_gain", "-11.69 dB"));
        tag.addField(new Mp4TagReverseDnsField("----:com.apple.iTunes:replaygain_track_peak", "com.apple.iTunes", "replaygain_track_peak", "1.103472"));
        tag.addField(new Mp4TagReverseDnsField("----:com.apple.iTunes:replaygain_album_gain", "com.apple.iTunes", "replaygain_album_gain", "-11.69 dB"));
        tag.addField(new Mp4TagReverseDnsField("----:com.apple.iTunes:replaygain_album_peak", "com.apple.iTunes", "replaygain_album_peak", "1.103472"));

        Map<String, Float> result = ReplayGainTagExtractor.parseMp4Tags(tag);
        assertNotNull(result);
        assertEquals(-11.69f, result.get("REPLAYGAIN_TRACK_GAIN"), DELTA);
        assertEquals(1.103472f, result.get("REPLAYGAIN_TRACK_PEAK"), DELTA);
        assertEquals(-11.69f, result.get("REPLAYGAIN_ALBUM_GAIN"), DELTA);
        assertEquals(1.103472f, result.get("REPLAYGAIN_ALBUM_PEAK"), DELTA);
    }

    @Test
    public void testParseId3Tags_withUnrelatedTxxxAndLowercase() throws Exception {
        ID3v24Tag tag = new ID3v24Tag();

        ID3v24Frame unrelated = new ID3v24Frame("TXXX");
        unrelated.setBody(new FrameBodyTXXX((byte) 0, "MusicBrainz Album Id", "abc-123"));
        tag.addField(unrelated);

        ID3v24Frame trackGain = new ID3v24Frame("TXXX");
        trackGain.setBody(new FrameBodyTXXX((byte) 0, "replaygain_track_gain", "-7.50 dB"));
        tag.addField(trackGain);

        ID3v24Frame trackPeak = new ID3v24Frame("TXXX");
        trackPeak.setBody(new FrameBodyTXXX((byte) 0, "REPLAYGAIN_TRACK_PEAK", "0.987654"));
        tag.addField(trackPeak);

        Map<String, Float> result = ReplayGainTagExtractor.parseId3Tags(tag, new org.jaudiotagger.audio.AudioFile());
        assertNotNull(result);
        assertEquals(-7.50f, result.get("REPLAYGAIN_TRACK_GAIN"), DELTA);
        assertEquals(0.987654f, result.get("REPLAYGAIN_TRACK_PEAK"), DELTA);
    }
}
