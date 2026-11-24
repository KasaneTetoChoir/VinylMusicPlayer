package com.poupa.vinylmusicplayer.discog.tagging;

import androidx.annotation.NonNull;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.TagTextField;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class ReplayGainTagExtractor {
  // Normalize all tags using the Vorbis ones
  private static final String REPLAYGAIN_TRACK_GAIN = "REPLAYGAIN_TRACK_GAIN";
  private static final String REPLAYGAIN_ALBUM_GAIN = "REPLAYGAIN_ALBUM_GAIN";
  private static final String REPLAYGAIN_TRACK_PEAK = "REPLAYGAIN_TRACK_PEAK";
  private static final String REPLAYGAIN_ALBUM_PEAK = "REPLAYGAIN_ALBUM_PEAK";

  public static class ReplayGainValues {float track = 0; float album = 0; float peakTrack = 1.0f; float peakAlbum = 1.0f;}

  static ReplayGainValues setReplayGainValues(@NonNull final AudioFile file) {
    Map<String, Float> tags = null;

    try {
      Tag tag = file.getTag();

      if (tag instanceof VorbisCommentTag || tag instanceof FlacTag) {
        tags = parseTags(tag);
      } else if (tag instanceof Mp4Tag) {
        tags = parseMp4Tags(tag);
      } else {
        tags = parseId3Tags(tag, file);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    ReplayGainValues result = new ReplayGainValues();
    if (tags != null && !tags.isEmpty()) {
      if (tags.containsKey(REPLAYGAIN_TRACK_GAIN)) {
        result.track = tags.get(REPLAYGAIN_TRACK_GAIN);
      }
      if (tags.containsKey(REPLAYGAIN_TRACK_PEAK)) {
        result.peakTrack = tags.get(REPLAYGAIN_TRACK_PEAK);
      }
      if (tags.containsKey(REPLAYGAIN_ALBUM_GAIN)) {
        result.album = tags.get(REPLAYGAIN_ALBUM_GAIN);
      }
      if (tags.containsKey(REPLAYGAIN_ALBUM_PEAK)) {
        result.peakAlbum = tags.get(REPLAYGAIN_ALBUM_PEAK);
      }
    }
    return result;
  }

  private static boolean isReplayGainKey(String key) {
    return REPLAYGAIN_TRACK_GAIN.equals(key)
        || REPLAYGAIN_ALBUM_GAIN.equals(key)
        || REPLAYGAIN_TRACK_PEAK.equals(key)
        || REPLAYGAIN_ALBUM_PEAK.equals(key);
  }

  static Map<String, Float> parseId3Tags(Tag tag, @NonNull final AudioFile file) throws Exception {
    Map<String, Float> tags = new HashMap<>();

    if (tag != null) {
      for (String frameId : new String[]{"TXXX", "TXX"}) {
        if (!tag.hasField(frameId)) {
          continue;
        }
        for (TagField field : tag.getFields(frameId)) {
          try {
            String key = null;
            String value = null;

            if (field instanceof AbstractID3v2Frame
                && ((AbstractID3v2Frame) field).getBody() instanceof FrameBodyTXXX) {
              FrameBodyTXXX body = (FrameBodyTXXX) ((AbstractID3v2Frame) field).getBody();
              key = body.getDescription();
              value = body.getFirstTextValue();
            } else {
              String[] data = field.toString().split(";", 2);
              if (data.length == 2 && data[0].length() > 14) {
                key = data[0].substring(13, data[0].length() - 1);
                value = data[1];
              }
            }

            if (key != null && value != null) {
              key = key.trim().toUpperCase(Locale.ROOT);
              if ("TRACK".equals(key)) {
                key = REPLAYGAIN_TRACK_GAIN;
              } else if ("ALBUM".equals(key)) {
                key = REPLAYGAIN_ALBUM_GAIN;
              }
              if (isReplayGainKey(key)) {
                tags.put(key, parseFloat(value));
              }
            }
          } catch (Exception ignored) {
          }
        }
      }
    }

    if (tags.isEmpty()) {
      return parseLameHeader(file);
    }

    return tags;
  }

  static Map<String, Float> parseTags(Tag tag) {
    Map<String, Float> tags = new HashMap<>();

    if (tag.hasField(REPLAYGAIN_TRACK_GAIN)) {
      tags.put(REPLAYGAIN_TRACK_GAIN, parseFloat(tag.getFirst(REPLAYGAIN_TRACK_GAIN)));
    }
    if (tag.hasField(REPLAYGAIN_TRACK_PEAK)) {
      tags.put(REPLAYGAIN_TRACK_PEAK, parseFloat(tag.getFirst(REPLAYGAIN_TRACK_PEAK)));
    }
    if (tag.hasField(REPLAYGAIN_ALBUM_GAIN)) {
      tags.put(REPLAYGAIN_ALBUM_GAIN, parseFloat(tag.getFirst(REPLAYGAIN_ALBUM_GAIN)));
    }
    if (tag.hasField(REPLAYGAIN_ALBUM_PEAK)) {
      tags.put(REPLAYGAIN_ALBUM_PEAK, parseFloat(tag.getFirst(REPLAYGAIN_ALBUM_PEAK)));
    }

    return tags;
  }

  static Map<String, Float> parseMp4Tags(Tag tag) {
    Map<String, Float> tags = parseTags(tag);

    final String ITUNES_PREFIX = "----:com.apple.iTunes:";
    Iterator<TagField> it = tag.getFields();
    while (it.hasNext()) {
      TagField field = it.next();
      String key = null;
      if (field instanceof Mp4TagReverseDnsField) {
        String descriptor = ((Mp4TagReverseDnsField) field).getDescriptor();
        if (descriptor != null) {
          key = descriptor.trim().toUpperCase(Locale.ROOT);
        }
      }
      if (key == null) {
        String id = field.getId();
        if (id != null && id.length() > ITUNES_PREFIX.length()
            && id.regionMatches(true, 0, ITUNES_PREFIX, 0, ITUNES_PREFIX.length())) {
          key = id.substring(ITUNES_PREFIX.length()).trim().toUpperCase(Locale.ROOT);
        }
      }

      if (key != null && isReplayGainKey(key) && !tags.containsKey(key)) {
        String value = (field instanceof TagTextField)
            ? ((TagTextField) field).getContent()
            : field.toString();
        tags.put(key, parseFloat(value));
      }
    }

    return tags;
  }

  private static Map<String, Float> parseLameHeader(@NonNull final AudioFile file) throws IOException {
    // Method taken from adrian-bl/bastp library
    // Peak values are as per http://gabriel.mp3-tech.org/mp3infotag.html
    Map<String, Float> tags = new HashMap<>();
    try (RandomAccessFile s = new RandomAccessFile(file.getFile(), "r")) {
      long baseOffset = 0;
      byte[] id3Header = new byte[10];
      if (s.read(id3Header) == 10
          && id3Header[0] == 'I' && id3Header[1] == 'D' && id3Header[2] == '3') {
        int id3Size = ((id3Header[6] & 0x7F) << 21)
            | ((id3Header[7] & 0x7F) << 14)
            | ((id3Header[8] & 0x7F) << 7)
            | (id3Header[9] & 0x7F);
        baseOffset = 10L + id3Size;
      }

      byte[] chunk = new byte[12];
      s.seek(baseOffset + 0x24);
      if (s.read(chunk) < 4) {
        return tags;
      }

      String lameMark = new String(chunk, 0, 4, "ISO-8859-1");

      if (lameMark.equals("Info") || lameMark.equals("Xing")) {
        s.seek(baseOffset + 0xA7);
        s.read(chunk);

        int rawPeak = b2be32(chunk);
        if (rawPeak != 0) {
          float peak = Float.intBitsToFloat(rawPeak);
          if (!Float.isNaN(peak)) {
            tags.put(REPLAYGAIN_TRACK_PEAK, peak);
            tags.put(REPLAYGAIN_ALBUM_PEAK, peak);
          }
        }

        s.read(chunk);

        int raw = b2be32(chunk);
        int gtrk_raw = raw >> 16;     /* first 16 bits are the raw track gain value */
        int galb_raw = raw & 0xFFFF;  /* the rest is for the album gain value       */

        float gtrk_val = (float) (gtrk_raw & 0x01FF) / 10;
        float galb_val = (float) (galb_raw & 0x01FF) / 10;

        gtrk_val = ((gtrk_raw & 0x0200) != 0 ? -1 * gtrk_val : gtrk_val);
        galb_val = ((galb_raw & 0x0200) != 0 ? -1 * galb_val : galb_val);

        if ((gtrk_raw & 0xE000) == 0x2000) {
          tags.put(REPLAYGAIN_TRACK_GAIN, gtrk_val);
        }
        if ((galb_raw & 0xE000) == 0x4000) {
          tags.put(REPLAYGAIN_ALBUM_GAIN, galb_val);
        }
      }
    }

    return tags;
  }

  private static int b2le32(byte[] b) {
    int r = 0;
    for(int i=0; i<4; i++) {
      r |= ( b2u(b[i]) << (8*i) );
    }
    return r;
  }

  private static int b2be32(byte[] b) {
    return swap32(b2le32(b));
  }

  private static int swap32(int i) {
    return((i&0xff)<<24)+((i&0xff00)<<8)+((i&0xff0000)>>8)+((i>>24)&0xff);
  }

  private static int b2u(byte x) {
    return (x & 0xFF);
  }

  static float parseFloat(String s) {
    if (s == null) return 0.0f;
    float result = 0.0f;
    try {
      s = s.replaceAll("[^0-9.-]", "").trim();
      if (!s.isEmpty() && !s.equals("-") && !s.equals(".") && !s.equals("-.")) {
        result = Float.parseFloat(s);
      }
    } catch (Exception ignored) {
    }

    return result;
  }
}
