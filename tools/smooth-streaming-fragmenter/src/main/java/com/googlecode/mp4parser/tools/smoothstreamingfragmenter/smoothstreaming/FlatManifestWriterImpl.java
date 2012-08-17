/*
 * Copyright 2012 Sebastian Annies, Hamburg
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.mp4parser.tools.smoothstreamingfragmenter.smoothstreaming;

import com.coremedia.iso.Hex;
import com.coremedia.iso.boxes.SampleDescriptionBox;
import com.coremedia.iso.boxes.SoundMediaHeaderBox;
import com.coremedia.iso.boxes.VideoMediaHeaderBox;
import com.coremedia.iso.boxes.h264.AvcConfigurationBox;
import com.coremedia.iso.boxes.sampleentry.AudioSampleEntry;
import com.coremedia.iso.boxes.sampleentry.VisualSampleEntry;
import com.googlecode.mp4parser.Version;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.adaptivestreaming.AbstractManifestWriter;
import com.googlecode.mp4parser.authoring.builder.FragmentIntersectionFinder;
import com.googlecode.mp4parser.boxes.DTSSpecificBox;
import com.googlecode.mp4parser.boxes.EC3SpecificBox;
import com.googlecode.mp4parser.boxes.mp4.ESDescriptorBox;
import com.googlecode.mp4parser.boxes.mp4.objectdescriptors.AudioSpecificConfig;
import nu.xom.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

public class FlatManifestWriterImpl extends AbstractManifestWriter {
    private static final Logger LOG = Logger.getLogger(FlatManifestWriterImpl.class.getName());

    protected FlatManifestWriterImpl(FragmentIntersectionFinder intersectionFinder) {
        super(intersectionFinder);
    }

    /**
     * Overwrite this method in subclasses to add your specialities.
     *
     * @param manifest the original manifest
     * @return your customized version of the manifest
     */
    protected Document customizeManifest(Document manifest) {
        return manifest;
    }

    @Override
    public String getManifest(Movie movie) throws IOException {

        LinkedList<VideoQuality> videoQualities = new LinkedList<VideoQuality>();
        long videoTimescale = -1;

        LinkedList<AudioQuality> audioQualities = new LinkedList<AudioQuality>();
        long audioTimescale = -1;

        for (Track track : movie.getTracks()) {
            if (track.getMediaHeaderBox() instanceof VideoMediaHeaderBox) {
                videoFragmentsDurations = checkFragmentsAlign(videoFragmentsDurations, calculateFragmentDurations(track, movie));
                SampleDescriptionBox stsd = track.getSampleDescriptionBox();
                videoQualities.add(getVideoQuality(track, (VisualSampleEntry) stsd.getSampleEntry()));
                if (videoTimescale == -1) {
                    videoTimescale = track.getTrackMetaData().getTimescale();
                } else {
                    assert videoTimescale == track.getTrackMetaData().getTimescale();
                }
            }
            if (track.getMediaHeaderBox() instanceof SoundMediaHeaderBox) {
                audioFragmentsDurations = checkFragmentsAlign(audioFragmentsDurations, calculateFragmentDurations(track, movie));
                SampleDescriptionBox stsd = track.getSampleDescriptionBox();
                audioQualities.add(getAudioQuality(track, (AudioSampleEntry) stsd.getSampleEntry()));
                if (audioTimescale == -1) {
                    audioTimescale = track.getTrackMetaData().getTimescale();
                } else {
                    assert audioTimescale == track.getTrackMetaData().getTimescale();
                }

            }
        }

        Element smoothStreamingMedia = new Element("SmoothStreamingMedia");
        smoothStreamingMedia.addAttribute(new Attribute("MajorVersion", "2"));
        smoothStreamingMedia.addAttribute(new Attribute("MinorVersion", "1"));
// silverlight ignores the timescale attr        smoothStreamingMedia.addAttribute(new Attribute("TimeScale", Long.toString(movieTimeScale)));
        smoothStreamingMedia.addAttribute(new Attribute("Duration", "0"));
        smoothStreamingMedia.appendChild(new Comment(Version.VERSION));
        Element videoStreamIndex = new Element("StreamIndex");
        videoStreamIndex.addAttribute(new Attribute("Type", "video"));
        videoStreamIndex.addAttribute(new Attribute("TimeScale", Long.toString(videoTimescale))); // silverlight ignores the timescale attr
        videoStreamIndex.addAttribute(new Attribute("Chunks", Integer.toString(videoFragmentsDurations.length)));
        videoStreamIndex.addAttribute(new Attribute("Url", "video/{bitrate}/{start time}"));
        videoStreamIndex.addAttribute(new Attribute("QualityLevels", Integer.toString(videoQualities.size())));
        smoothStreamingMedia.appendChild(videoStreamIndex);

        for (int i = 0; i < videoQualities.size(); i++) {
            VideoQuality vq = videoQualities.get(i);
            Element qualityLevel = new Element("QualityLevel");
            qualityLevel.addAttribute(new Attribute("Index", Integer.toString(i)));
            qualityLevel.addAttribute(new Attribute("Bitrate", Long.toString(vq.bitrate)));
            qualityLevel.addAttribute(new Attribute("FourCC", vq.fourCC));
            qualityLevel.addAttribute(new Attribute("MaxWidth", Long.toString(vq.width)));
            qualityLevel.addAttribute(new Attribute("MaxHeight", Long.toString(vq.height)));
            qualityLevel.addAttribute(new Attribute("CodecPrivateData", vq.codecPrivateData));
            qualityLevel.addAttribute(new Attribute("NALUnitLengthField", Integer.toString(vq.nalLength)));
            videoStreamIndex.appendChild(qualityLevel);
        }

        for (int i = 0; i < videoFragmentsDurations.length; i++) {
            Element c = new Element("c");
            c.addAttribute(new Attribute("n", Integer.toString(i)));
            c.addAttribute(new Attribute("d", Long.toString(videoFragmentsDurations[i])));
            videoStreamIndex.appendChild(c);
        }

        if (audioFragmentsDurations != null) {
            Element audioStreamIndex = new Element("StreamIndex");
            audioStreamIndex.addAttribute(new Attribute("Type", "audio"));
            audioStreamIndex.addAttribute(new Attribute("TimeScale", Long.toString(audioTimescale))); // silverlight ignores the timescale attr
            audioStreamIndex.addAttribute(new Attribute("Chunks", Integer.toString(audioFragmentsDurations.length)));
            audioStreamIndex.addAttribute(new Attribute("Url", "audio/{bitrate}/{start time}"));
            audioStreamIndex.addAttribute(new Attribute("QualityLevels", Integer.toString(audioQualities.size())));
            smoothStreamingMedia.appendChild(audioStreamIndex);

            for (int i = 0; i < audioQualities.size(); i++) {
                AudioQuality aq = audioQualities.get(i);
                Element qualityLevel = new Element("QualityLevel");
                qualityLevel.addAttribute(new Attribute("Index", Integer.toString(i)));
                qualityLevel.addAttribute(new Attribute("FourCC", aq.fourCC));
                qualityLevel.addAttribute(new Attribute("Bitrate", Long.toString(aq.bitrate)));
                qualityLevel.addAttribute(new Attribute("AudioTag", Integer.toString(aq.audioTag)));
                qualityLevel.addAttribute(new Attribute("SamplingRate", Long.toString(aq.samplingRate)));
                qualityLevel.addAttribute(new Attribute("Channels", Integer.toString(aq.channels)));
                qualityLevel.addAttribute(new Attribute("BitsPerSample", Integer.toString(aq.bitPerSample)));
                qualityLevel.addAttribute(new Attribute("PacketSize", Integer.toString(aq.packetSize)));
                qualityLevel.addAttribute(new Attribute("CodecPrivateData", aq.codecPrivateData));
                audioStreamIndex.appendChild(qualityLevel);
            }
            for (int i = 0; i < audioFragmentsDurations.length; i++) {
                Element c = new Element("c");
                c.addAttribute(new Attribute("n", Integer.toString(i)));
                c.addAttribute(new Attribute("d", Long.toString(audioFragmentsDurations[i])));
                audioStreamIndex.appendChild(c);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Serializer serializer = new Serializer(baos);
        serializer.setIndent(4);
        serializer.write(customizeManifest(new Document(smoothStreamingMedia)));

        return baos.toString("UTF-8");

    }

    private AudioQuality getAudioQuality(Track track, AudioSampleEntry ase) {
        if (getFormat(ase).equals("mp4a")) {
            return getAacAudioQuality(track, ase);
        } else if (getFormat(ase).equals("ec-3")) {
            return getEc3AudioQuality(track, ase);
        } else if (getFormat(ase).startsWith("dts")) {
            return getDtsAudioQuality(track, ase);
        } else {
            throw new InternalError("I don't know what to do with audio of type " + getFormat(ase));
        }

    }

    private AudioQuality getAacAudioQuality(Track track, AudioSampleEntry ase) {
        AudioQuality l = new AudioQuality();
        final ESDescriptorBox esDescriptorBox = ase.getBoxes(ESDescriptorBox.class).get(0);
        final AudioSpecificConfig audioSpecificConfig = esDescriptorBox.getEsDescriptor().getDecoderConfigDescriptor().getAudioSpecificInfo();
        if (audioSpecificConfig.getSbrPresentFlag() == 1) {
            l.fourCC = "AACH";
        } else if (audioSpecificConfig.getPsPresentFlag() == 1) {
            l.fourCC = "AACP"; //I'm not sure if that's what MS considers as AAC+ - because actually AAC+ and AAC-HE should be the same...
        } else {
            l.fourCC = "AACL";
        }
        l.bitrate = getBitrate(track);
        l.audioTag = 255;
        l.samplingRate = ase.getSampleRate();
        l.channels = ase.getChannelCount();
        l.bitPerSample = ase.getSampleSize();
        l.packetSize = 4;
        l.codecPrivateData = getAudioCodecPrivateData(audioSpecificConfig);
        //Index="0" Bitrate="103000" AudioTag="255" SamplingRate="44100" Channels="2" BitsPerSample="16" packetSize="4" CodecPrivateData=""
        return l;
    }

    private AudioQuality getEc3AudioQuality(Track track, AudioSampleEntry ase) {
        final EC3SpecificBox ec3SpecificBox = ase.getBoxes(EC3SpecificBox.class).get(0);
        if (ec3SpecificBox == null) {
            throw new RuntimeException("EC-3 track misses EC3SpecificBox!");
        }

        short nfchans = 0; //full bandwidth channels
        short lfechans = 0;
        byte dWChannelMaskFirstByte = 0;
        byte dWChannelMaskSecondByte = 0;
        for (EC3SpecificBox.Entry entry : ec3SpecificBox.getEntries()) {
            /*
            Table 4.3: Audio coding mode
            acmod Audio coding mode Nfchans Channel array ordering
            000 1 + 1 2 Ch1, Ch2
            001 1/0 1 C
            010 2/0 2 L, R
            011 3/0 3 L, C, R
            100 2/1 3 L, R, S
            101 3/1 4 L, C, R, S
            110 2/2 4 L, R, SL, SR
            111 3/2 5 L, C, R, SL, SR

            Table F.2: Chan_loc field bit assignments
            Bit Location
            0 Lc/Rc pair
            1 Lrs/Rrs pair
            2 Cs
            3 Ts
            4 Lsd/Rsd pair
            5 Lw/Rw pair
            6 Lvh/Rvh pair
            7 Cvh
            8 LFE2
            */
            switch (entry.acmod) {
                case 0: //1+1; Ch1, Ch2
                    nfchans += 2;
                    throw new RuntimeException("Smooth Streaming doesn't support DDP 1+1 mode");
                case 1: //1/0; C
                    nfchans += 1;
                    if (entry.num_dep_sub > 0) {
                        DependentSubstreamMask dependentSubstreamMask = new DependentSubstreamMask(dWChannelMaskFirstByte, dWChannelMaskSecondByte, entry).process();
                        dWChannelMaskFirstByte |= dependentSubstreamMask.getdWChannelMaskFirstByte();
                        dWChannelMaskSecondByte |= dependentSubstreamMask.getdWChannelMaskSecondByte();
                    } else {
                        dWChannelMaskFirstByte |= 0x20;
                    }
                    break;
                case 2: //2/0; L, R
                    nfchans += 2;
                    if (entry.num_dep_sub > 0) {
                        DependentSubstreamMask dependentSubstreamMask = new DependentSubstreamMask(dWChannelMaskFirstByte, dWChannelMaskSecondByte, entry).process();
                        dWChannelMaskFirstByte |= dependentSubstreamMask.getdWChannelMaskFirstByte();
                        dWChannelMaskSecondByte |= dependentSubstreamMask.getdWChannelMaskSecondByte();
                    } else {
                        dWChannelMaskFirstByte |= 0xC0;
                    }
                    break;
                case 3: //3/0; L, C, R
                    nfchans += 3;
                    if (entry.num_dep_sub > 0) {
                        DependentSubstreamMask dependentSubstreamMask = new DependentSubstreamMask(dWChannelMaskFirstByte, dWChannelMaskSecondByte, entry).process();
                        dWChannelMaskFirstByte |= dependentSubstreamMask.getdWChannelMaskFirstByte();
                        dWChannelMaskSecondByte |= dependentSubstreamMask.getdWChannelMaskSecondByte();
                    } else {
                        dWChannelMaskFirstByte |= 0xE0;
                    }
                    break;
                case 4: //2/1; L, R, S
                    nfchans += 3;
                    if (entry.num_dep_sub > 0) {
                        DependentSubstreamMask dependentSubstreamMask = new DependentSubstreamMask(dWChannelMaskFirstByte, dWChannelMaskSecondByte, entry).process();
                        dWChannelMaskFirstByte |= dependentSubstreamMask.getdWChannelMaskFirstByte();
                        dWChannelMaskSecondByte |= dependentSubstreamMask.getdWChannelMaskSecondByte();
                    } else {
                        dWChannelMaskFirstByte |= 0xC0;
                        dWChannelMaskSecondByte |= 0x80;
                    }
                    break;
                case 5: //3/1; L, C, R, S
                    nfchans += 4;
                    if (entry.num_dep_sub > 0) {
                        DependentSubstreamMask dependentSubstreamMask = new DependentSubstreamMask(dWChannelMaskFirstByte, dWChannelMaskSecondByte, entry).process();
                        dWChannelMaskFirstByte |= dependentSubstreamMask.getdWChannelMaskFirstByte();
                        dWChannelMaskSecondByte |= dependentSubstreamMask.getdWChannelMaskSecondByte();
                    } else {
                        dWChannelMaskFirstByte |= 0xE0;
                        dWChannelMaskSecondByte |= 0x80;
                    }
                    break;
                case 6: //2/2; L, R, SL, SR
                    nfchans += 4;
                    if (entry.num_dep_sub > 0) {
                        DependentSubstreamMask dependentSubstreamMask = new DependentSubstreamMask(dWChannelMaskFirstByte, dWChannelMaskSecondByte, entry).process();
                        dWChannelMaskFirstByte |= dependentSubstreamMask.getdWChannelMaskFirstByte();
                        dWChannelMaskSecondByte |= dependentSubstreamMask.getdWChannelMaskSecondByte();
                    } else {
                        dWChannelMaskFirstByte |= 0xCC;
                    }
                    break;
                case 7: //3/2; L, C, R, SL, SR
                    nfchans += 5;
                    if (entry.num_dep_sub > 0) {
                        DependentSubstreamMask dependentSubstreamMask = new DependentSubstreamMask(dWChannelMaskFirstByte, dWChannelMaskSecondByte, entry).process();
                        dWChannelMaskFirstByte |= dependentSubstreamMask.getdWChannelMaskFirstByte();
                        dWChannelMaskSecondByte |= dependentSubstreamMask.getdWChannelMaskSecondByte();
                    } else {
                        dWChannelMaskFirstByte |= 0xEC;
                    }
                    break;
            }
            if (entry.lfeon == 1) {
                lfechans ++;
                dWChannelMaskFirstByte |= 0x10;
            }
        }

        final ByteBuffer waveformatex = ByteBuffer.allocate(22);
        waveformatex.put(new byte[]{0x00, 0x06}); //1536 wSamplesPerBlock - little endian
        waveformatex.put(dWChannelMaskFirstByte);
        waveformatex.put(dWChannelMaskSecondByte);
        waveformatex.put(new byte[]{0x00, 0x00}); //pad dwChannelMask to 32bit
        waveformatex.put(new byte[]{(byte)0xAF, (byte)0x87, (byte)0xFB, (byte)0xA7, 0x02, 0x2D, (byte)0xFB, 0x42, (byte)0xA4, (byte)0xD4, 0x05, (byte)0xCD, (byte)0x93, (byte)0x84, 0x3B, (byte)0xDD}); //SubFormat - Dolby Digital Plus GUID

        final ByteBuffer dec3Content = ByteBuffer.allocate((int) ec3SpecificBox.getContentSize());
        ec3SpecificBox.getContent(dec3Content);

        AudioQuality l = new AudioQuality();
        l.fourCC = "EC-3";
        l.bitrate = getBitrate(track);
        l.audioTag = 65534;
        l.samplingRate = ase.getSampleRate();
        l.channels = nfchans + lfechans;
        l.bitPerSample = 16;
        l.packetSize = track.getSamples().get(0).limit(); //assuming all are same size
        l.codecPrivateData = Hex.encodeHex(waveformatex.array()) + Hex.encodeHex(dec3Content.array()); //append EC3SpecificBox (big endian) at the end of waveformatex
        return l;
    }

    private AudioQuality getDtsAudioQuality(Track track, AudioSampleEntry ase) {
        final DTSSpecificBox dtsSpecificBox = ase.getBoxes(DTSSpecificBox.class).get(0);
        if (dtsSpecificBox == null) {
            throw new RuntimeException("DTS track misses DTSSpecificBox!");
        }
        byte dWChannelMaskFirstByte = 0;
        byte dWChannelMaskSecondByte = 0;

        final ByteBuffer waveformatex = ByteBuffer.allocate(22);
        final int frameDuration = dtsSpecificBox.getFrameDuration();
        short samplesPerBlock = 0;
        switch (frameDuration) {
            case 0:
                samplesPerBlock = 512;
                break;
            case 1:
                samplesPerBlock = 1024;
                break;
            case 2:
                samplesPerBlock = 2048;
                break;
            case 3:
                samplesPerBlock = 4096;
                break;
        }
        waveformatex.putShort(samplesPerBlock);
        waveformatex.putInt(dtsSpecificBox.getChannelLayout());
        waveformatex.put(new byte[]{(byte)0xAE, (byte)0xE4, (byte)0xBF, (byte)0x5E, (byte)0x61, (byte)0x5E, (byte)0x41, (byte)0x87, (byte)0x92, (byte)0xFC, (byte)0xA4, (byte)0x81, (byte)0x26, (byte)0x99, (byte)0x02, (byte)0x11}); //DTS-HD GUID

        final ByteBuffer dtsCodecPrivateData= ByteBuffer.allocate(8);
        dtsCodecPrivateData.put((byte) dtsSpecificBox.getStreamConstruction());
        dtsCodecPrivateData.putInt(dtsSpecificBox.getChannelLayout());
        byte dtsFlags = (byte) (dtsSpecificBox.getMultiAssetFlag() << 1);
        dtsFlags |= dtsSpecificBox.getLBRDurationMod();
        dtsCodecPrivateData.put(dtsFlags);
        dtsCodecPrivateData.put(new byte[]{0x00, 0x00}); //reserved

        AudioQuality l = new AudioQuality();
        l.fourCC = getFormat(ase);
        l.bitrate = dtsSpecificBox.getAvgBitRate();
        l.audioTag = 65534;
        l.samplingRate = dtsSpecificBox.getDTSSamplingFrequency();
        l.channels = getNumChannels(dtsSpecificBox);
        l.bitPerSample = 16;
        l.packetSize = track.getSamples().get(0).limit(); //assuming all are same size
        l.codecPrivateData = Hex.encodeHex(waveformatex.array()) + Hex.encodeHex(dtsCodecPrivateData.array());
        return l;

    }

    private int getNumChannels(DTSSpecificBox dtsSpecificBox) {
        final int channelLayout = dtsSpecificBox.getChannelLayout();
        int numChannels = 0;
        if ((channelLayout & 0x0001) == 0x0001) {
            //0001h Center in front of listener 1
            numChannels += 1;
        }
        if ((channelLayout & 0x0002) == 0x0002) {
            //0002h Left/Right in front 2
            numChannels += 2;
        }
        if ((channelLayout & 0x0004) == 0x0004) {
            //0004h Left/Right surround on side in rear 2
            numChannels += 2;
        }
        if ((channelLayout & 0x0008) == 0x0008) {
            //0008h Low frequency effects subwoofer 1
            numChannels += 1;
        }
        if ((channelLayout & 0x0010) == 0x0010) {
            //0010h Center surround in rear 1
            numChannels += 1;
        }
        if ((channelLayout & 0x0020) == 0x0020) {
            //0020h Left/Right height in front 2
            numChannels += 2;
        }
        if ((channelLayout & 0x0040) == 0x0040) {
            //0040h Left/Right surround in rear 2
            numChannels += 2;
        }
        if ((channelLayout & 0x0080) == 0x0080) {
            //0080h Center Height in front 1
            numChannels += 1;
        }
        if ((channelLayout & 0x0100) == 0x0100) {
            //0100h Over the listener’s head 1
            numChannels += 1;
        }
        if ((channelLayout & 0x0200) == 0x0200) {
            //0200h Between left/right and center in front 2
            numChannels += 2;
        }
        if ((channelLayout & 0x0400) == 0x0400) {
            //0400h Left/Right on side in front 2
            numChannels += 2;
        }
        if ((channelLayout & 0x0800) == 0x0800) {
            //0800h Left/Right surround on side 2
            numChannels += 2;
        }
        if ((channelLayout & 0x1000) == 0x1000) {
            //1000h Second low frequency effects subwoofer 1
            numChannels += 1;
        }
        if ((channelLayout & 0x2000) == 0x2000) {
            //2000h Left/Right height on side 2
            numChannels += 2;
        }
        if ((channelLayout & 0x4000) == 0x4000) {
            //4000h Center height in rear 1
            numChannels += 1;
        }
        if ((channelLayout & 0x8000) == 0x8000) {
            //8000h Left/Right height in rear 2
            numChannels += 2;
        }
        return numChannels;
    }

    private String getAudioCodecPrivateData(AudioSpecificConfig audioSpecificConfig) {
        byte[] configByteArray = audioSpecificConfig.getConfigBytes();
        return Hex.encodeHex(configByteArray);
    }

    private VideoQuality getVideoQuality(Track track, VisualSampleEntry vse) {
        VideoQuality l;
        if ("avc1".equals(getFormat(vse))) {
            AvcConfigurationBox avcConfigurationBox = vse.getBoxes(AvcConfigurationBox.class).get(0);
            l = new VideoQuality();
            l.bitrate = getBitrate(track);
            l.codecPrivateData = Hex.encodeHex(getAvcCodecPrivateData(avcConfigurationBox));
            l.fourCC = "AVC1";
            l.width = vse.getWidth();
            l.height = vse.getHeight();
            l.nalLength = avcConfigurationBox.getLengthSizeMinusOne() + 1;
        } else {
            throw new InternalError("I don't know how to handle video of type " + getFormat(vse));
        }
        return l;
    }

    private byte[] getAvcCodecPrivateData(AvcConfigurationBox avcConfigurationBox) {
        List<byte[]> sps = avcConfigurationBox.getSequenceParameterSets();
        List<byte[]> pps = avcConfigurationBox.getPictureParameterSets();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(new byte[]{0, 0, 0, 1});

            for (byte[] sp : sps) {
                baos.write(sp);
            }
            baos.write(new byte[]{0, 0, 0, 1});
            for (byte[] pp : pps) {
                baos.write(pp);
            }
        } catch (IOException ex) {
            throw new RuntimeException("ByteArrayOutputStream do not throw IOException ?!?!?");
        }
        return baos.toByteArray();
    }

    private class DependentSubstreamMask {
        private byte dWChannelMaskFirstByte;
        private byte dWChannelMaskSecondByte;
        private EC3SpecificBox.Entry entry;

        public DependentSubstreamMask(byte dWChannelMaskFirstByte, byte dWChannelMaskSecondByte, EC3SpecificBox.Entry entry) {
            this.dWChannelMaskFirstByte = dWChannelMaskFirstByte;
            this.dWChannelMaskSecondByte = dWChannelMaskSecondByte;
            this.entry = entry;
        }

        public byte getdWChannelMaskFirstByte() {
            return dWChannelMaskFirstByte;
        }

        public byte getdWChannelMaskSecondByte() {
            return dWChannelMaskSecondByte;
        }

        public DependentSubstreamMask process() {
            switch (entry.chan_loc) {
                case 0:
                    dWChannelMaskFirstByte |= 0x3;
                    break;
                case 1:
                    dWChannelMaskFirstByte |= 0xC;
                    break;
                case 2:
                    dWChannelMaskSecondByte |= 0x80;
                    break;
                case 3:
                    dWChannelMaskSecondByte |= 0x8;
                    break;
                case 6:
                    dWChannelMaskSecondByte |= 0x5;
                    break;
                case 7:
                    dWChannelMaskSecondByte |= 0x2;
                    break;
            }
            return this;
        }
    }
}
