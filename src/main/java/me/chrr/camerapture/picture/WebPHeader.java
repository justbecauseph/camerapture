package me.chrr.camerapture.picture;

import org.jetbrains.annotations.Nullable;

public final class WebPHeader {
   private static final int RIFF_HEADER_LENGTH = 16;
   public static final int MAX_DIMENSION = 16383;

   private WebPHeader() {
   }

   @Nullable
   public static WebPHeader.Size read(byte @Nullable [] data) {
      if (data == null || data.length < 16) {
         return null;
      }

      if (!matches(data, 0, "RIFF") || !matches(data, 8, "WEBP")) {
         return null;
      }

      if (matches(data, 12, "VP8 ")) {
         if (data.length < 30) {
            return null;
         } else {
            return u8(data, 23) == 157 && u8(data, 24) == 1 && u8(data, 25) == 42 ? new WebPHeader.Size(u16(data, 26) & 16383, u16(data, 28) & 16383) : null;
         }
      } else if (matches(data, 12, "VP8L")) {
         if (data.length >= 25 && u8(data, 20) == 47) {
            int bits = u32(data, 21);
            return new WebPHeader.Size((bits & 16383) + 1, (bits >>> 14 & 16383) + 1);
         } else {
            return null;
         }
      } else if (!matches(data, 12, "VP8X")) {
         return null;
      } else {
         return data.length < 30 ? null : new WebPHeader.Size(u24(data, 24) + 1, u24(data, 27) + 1);
      }
   }

   private static boolean matches(byte[] data, int offset, String fourCC) {
      for (int i = 0; i < 4; i++) {
         if (u8(data, offset + i) != fourCC.charAt(i)) {
            return false;
         }
      }

      return true;
   }

   private static int u8(byte[] data, int offset) {
      return data[offset] & 0xFF;
   }

   private static int u16(byte[] data, int offset) {
      return u8(data, offset) | u8(data, offset + 1) << 8;
   }

   private static int u24(byte[] data, int offset) {
      return u16(data, offset) | u8(data, offset + 2) << 16;
   }

   private static int u32(byte[] data, int offset) {
      return u24(data, offset) | u8(data, offset + 3) << 24;
   }

   public record Size(int width, int height) {
   }
}
