package me.chrr.camerapture;

import java.util.function.Consumer;

public class ByteCollector {
   private final Consumer<byte[]> callback;
   private byte[] bytes = null;
   private int offset = 0;

   public ByteCollector(Consumer<byte[]> callback) {
      this.callback = callback;
   }

   public int getCurrentLength() {
      return this.offset;
   }

   public boolean push(byte[] bytes, int bytesLeft) {
      if (bytesLeft >= 0 && bytes != null) {
         if (this.bytes == null) {
            long totalSize = (long)bytes.length + bytesLeft;
            if (totalSize > 10000000L) {
               return false;
            }

            this.bytes = new byte[(int)totalSize];
         }

         if (this.offset + bytes.length + bytesLeft != this.bytes.length) {
            return false;
         }

         System.arraycopy(bytes, 0, this.bytes, this.offset, bytes.length);
         this.offset += bytes.length;
         if (bytesLeft == 0) {
            this.callback.accept(this.bytes);
         }

         return true;
      } else {
         return false;
      }
   }

   public static void split(byte[] bytes, int sectionSize, ByteCollector.SectionConsumer callback) {
      int bytesLeft = bytes.length;
      int offset = 0;

      while (bytesLeft > 0) {
         int size = Math.min(sectionSize, bytesLeft);
         byte[] section;
         if (offset == 0 && size == bytes.length) {
            section = bytes;
         } else {
            section = new byte[size];
            System.arraycopy(bytes, offset, section, 0, size);
         }

         callback.accept(section, bytesLeft - size);
         offset += size;
         bytesLeft -= size;
      }
   }

   public interface SectionConsumer {
      void accept(byte[] var1, int var2);
   }
}
