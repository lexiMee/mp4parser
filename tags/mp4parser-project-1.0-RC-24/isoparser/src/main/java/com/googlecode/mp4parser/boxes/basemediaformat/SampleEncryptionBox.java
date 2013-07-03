package com.googlecode.mp4parser.boxes.basemediaformat;

import com.googlecode.mp4parser.boxes.AbstractSampleEncryptionBox;

/**
 * <h1>4cc = "{@value #TYPE}"</h1>
 * <pre>
 * aligned(8) class AbstractSampleEncryptionBox extends FullBox(‘uuid’, extended_type= 0xA2394F52-5A9B-4f14-A244-6C427C648DF4, version=0, flags=0)
 * {
 *  unsigned int (32) sample_count;
 *  {
 *   unsigned int(16) InitializationVector;
 *  }[ sample_count ]
 * }
 * </pre>
 */
public class SampleEncryptionBox extends AbstractSampleEncryptionBox {
    public static final String TYPE = "senc";

    /**
     * Creates a SampleEncryptionBox for non-h264 tracks.
     */
    public SampleEncryptionBox() {
        super(TYPE);

    }
}
