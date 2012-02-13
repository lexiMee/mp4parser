package com.googlecode.mp4parser.authoring.builder;

import com.coremedia.iso.*;
import com.coremedia.iso.boxes.*;
import com.coremedia.iso.boxes.mdat.ByteArraySampleImpl;
import com.coremedia.iso.boxes.mdat.FileChannelSampleImpl;
import com.coremedia.iso.boxes.mdat.Sample;
import com.googlecode.mp4parser.authoring.DateHelper;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.*;
import java.util.logging.Logger;

import static com.coremedia.iso.boxes.CastUtils.l2i;

/**
 * Creates a plain MP4 file from a video. Plain as plain can be.
 */
public class DefaultMp4Builder implements Mp4Builder {
    Set<StaticChunkOffsetBox> chunkOffsetBoxes = new HashSet<StaticChunkOffsetBox>();
    private static Logger LOG = Logger.getLogger(DefaultMp4Builder.class.getName());

    public IsoFile build(Movie movie) throws IOException {
        LOG.info("Creating movie " + movie);
        IsoFile isoFile = new IsoFile(null);
        isoFile.parse();
        // ouch that is ugly but I don't know how to do it else
        List<String> minorBrands = new LinkedList<String>();
        minorBrands.add("isom");
        minorBrands.add("iso2");
        minorBrands.add("avc1");

        isoFile.addBox(new FileTypeBox("isom", 0, minorBrands));
        isoFile.addBox(createMovieBox(movie));
        Box mdat = new InterleaveChunkMdat(movie);
        isoFile.addBox(mdat);
        /*
        dataOffset is where the first sample starts. Since we created the chunk offset boxes
        without knowing this offset and temporarely
         */
        long dataOffset = mdat.calculateOffset() + 8;
        for (StaticChunkOffsetBox chunkOffsetBox : chunkOffsetBoxes) {
            long[] offsets = chunkOffsetBox.getChunkOffsets();
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] += dataOffset;
            }
        }


        return isoFile;
    }

    private MovieBox createMovieBox(Movie movie) {
        MovieBox movieBox = new MovieBox();
        List<Box> movieBoxChildren = new LinkedList<Box>();
        MovieHeaderBox mvhd = new MovieHeaderBox();
        mvhd.setCreationTime(DateHelper.convert(new Date()));
        mvhd.setModificationTime(DateHelper.convert(new Date()));

        long movieTimeScale = getTimescale(movie);
        long duration = 0;

        for (Track track : movie.getTracks()) {
            long tracksDuration = getDuration(track) * movieTimeScale / track.getTrackMetaData().getTimescale();
            if (tracksDuration > duration) {
                duration = tracksDuration;
            }


        }

        mvhd.setDuration(duration);
        mvhd.setTimescale(movieTimeScale);
        // find the next available trackId
        long nextTrackId = 0;
        for (Track track : movie.getTracks()) {
            nextTrackId = nextTrackId < track.getTrackMetaData().getTrackId() ? track.getTrackMetaData().getTrackId() : nextTrackId;
        }
        mvhd.setNextTrackId(++nextTrackId);
        movieBoxChildren.add(mvhd);
        for (Track track : movie.getTracks()) {
            if (track.getType() != Track.Type.UNKNOWN) {
                movieBoxChildren.add(createTrackBox(track, movie));
            }
        }
        // metadata here
        movieBox.setBoxes(movieBoxChildren);
        return movieBox;

    }

    private TrackBox createTrackBox(Track track, Movie movie) {
        LOG.info("Creating Mp4TrackImpl " + track);
        TrackBox trackBox = new TrackBox();
        TrackHeaderBox tkhd = new TrackHeaderBox();
        int flags = 0;
        if (track.isEnabled()) {
            flags += 1;
        }

        if (track.isInMovie()) {
            flags += 2;
        }

        if (track.isInPreview()) {
            flags += 4;
        }

        if (track.isInPoster()) {
            flags += 8;
        }
        tkhd.setFlags(flags);

        tkhd.setAlternateGroup(track.getTrackMetaData().getGroup());
        tkhd.setCreationTime(DateHelper.convert(track.getTrackMetaData().getCreationTime()));
        // We need to take edit list box into account in trackheader duration
        // but as long as I don't support edit list boxes it is sufficient to
        // just translate media duration to movie timescale
        tkhd.setDuration(getDuration(track) * getTimescale(movie) / track.getTrackMetaData().getTimescale());
        tkhd.setHeight(track.getTrackMetaData().getHeight());
        tkhd.setWidth(track.getTrackMetaData().getWidth());
        tkhd.setLayer(track.getTrackMetaData().getLayer());
        tkhd.setModificationTime(DateHelper.convert(new Date()));
        tkhd.setTrackId(track.getTrackMetaData().getTrackId());
        tkhd.setVolume(track.getTrackMetaData().getVolume());
        trackBox.addBox(tkhd);

        EditBox edit = new EditBox();
        EditListBox editListBox = new EditListBox();
        editListBox.setEntries(Collections.singletonList(
                new EditListBox.Entry(editListBox, (long) (track.getTrackMetaData().getStartTime() * getTimescale(movie)), -1, 1)));
        edit.addBox(editListBox);
        trackBox.addBox(edit);

        MediaBox mdia = new MediaBox();
        trackBox.addBox(mdia);
        MediaHeaderBox mdhd = new MediaHeaderBox();
        mdhd.setCreationTime(DateHelper.convert(track.getTrackMetaData().getCreationTime()));
        mdhd.setDuration(getDuration(track));
        mdhd.setTimescale(track.getTrackMetaData().getTimescale());
        mdhd.setLanguage(track.getTrackMetaData().getLanguage());
        mdia.addBox(mdhd);
        HandlerBox hdlr = new HandlerBox();
        mdia.addBox(hdlr);
        switch (track.getType()) {
            case VIDEO:
                hdlr.setHandlerType("vide");
                break;
            case SOUND:
                hdlr.setHandlerType("soun");
                break;
            case HINT:
                hdlr.setHandlerType("hint");
                break;
            case TEXT:
                hdlr.setHandlerType("text");
                break;
            case AMF0:
                hdlr.setHandlerType("data");
                break;
            default:
                throw new RuntimeException("Dont know handler type " + track.getType());
        }

        MediaInformationBox minf = new MediaInformationBox();
        switch (track.getType()) {
            case VIDEO:
                VideoMediaHeaderBox vmhd = new VideoMediaHeaderBox();
                minf.addBox(vmhd);
                break;
            case SOUND:
                SoundMediaHeaderBox smhd = new SoundMediaHeaderBox();
                minf.addBox(smhd);
                break;
            case HINT:
                HintMediaHeaderBox hmhd = new HintMediaHeaderBox();
                minf.addBox(hmhd);
                break;
            case TEXT:
            case AMF0:
            case NULL:
                NullMediaHeaderBox nmhd = new NullMediaHeaderBox();
                minf.addBox(nmhd);
                break;
        }
        // dinf: all these three boxes tell us is that the actual
        // data is in the current file and not somewhere external
        DataInformationBox dinf = new DataInformationBox();
        DataReferenceBox dref = new DataReferenceBox();
        dinf.addBox(dref);
        DataEntryUrlBox url = new DataEntryUrlBox();
        url.setFlags(1);
        dref.addBox(url);
        minf.addBox(dinf);
        //

        SampleTableBox stbl = new SampleTableBox();

        stbl.addBox(track.getSampleDescriptionBox());

        if (track.getDecodingTimeEntries() != null && !track.getDecodingTimeEntries().isEmpty()) {
            TimeToSampleBox stts = new TimeToSampleBox();
            stts.setEntries(track.getDecodingTimeEntries());
            stbl.addBox(stts);
        }
        if (track.getCompositionTimeEntries() != null && !track.getCompositionTimeEntries().isEmpty()) {
            CompositionTimeToSample ctts = new CompositionTimeToSample();
            ctts.setEntries(track.getCompositionTimeEntries());
            stbl.addBox(ctts);
        }

        if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
            SyncSampleBox stss = new SyncSampleBox();
            stss.setSampleNumber(track.getSyncSamples());
            stbl.addBox(stss);
        }
        if (track.getSampleDependencies() != null && !track.getSampleDependencies().isEmpty()) {
            SampleDependencyTypeBox sdtp = new SampleDependencyTypeBox();
            sdtp.setEntries(track.getSampleDependencies());
            stbl.addBox(sdtp);
        }
        long chunkSize[] = getChunkSizes(track, movie);
        SampleToChunkBox stsc = new SampleToChunkBox();
        stsc.setEntries(new LinkedList<SampleToChunkBox.Entry>());
        long lastChunkSize = Integer.MIN_VALUE; // to be sure the first chunks hasn't got the same size
        for (int i = 0; i < chunkSize.length; i++) {
            // The sample description index references the sample description box
            // that describes the samples of this chunk. My Tracks cannot have more
            // than one sample description box. Therefore 1 is always right
            // the first chunk has the number '1'
            if (lastChunkSize != chunkSize[i]) {
                stsc.getEntries().add(new SampleToChunkBox.Entry(i + 1, chunkSize[i], 1));
                lastChunkSize = chunkSize[i];
            }
        }
        stbl.addBox(stsc);

        SampleSizeBox stsz = new SampleSizeBox();
        long[] sizes = new long[track.getSamples().size()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = track.getSamples().get(i).getSize();
        }
        stsz.setSampleSizes(sizes);

        stbl.addBox(stsz);
        // The ChunkOffsetBox we create here is just a stub
        // since we haven't created the whole structure we can't tell where the
        // first chunk starts (mdat box). So I just let the chunk offset
        // start at zero and I will add the mdat offset later.
        StaticChunkOffsetBox stco = new StaticChunkOffsetBox();
        this.chunkOffsetBoxes.add(stco);
        long offset = 0;
        long[] chunkOffset = new long[chunkSize.length];
        // all tracks have the same number of chunks
        LOG.fine("Calculating chunk offsets for track_" + track.getTrackMetaData().getTrackId());
        for (int i = 0; i < chunkSize.length; i++) {
            // The filelayout will be:
            // chunk_1_track_1,... ,chunk_1_track_n, chunk_2_track_1,... ,chunk_2_track_n, ... , chunk_m_track_1,... ,chunk_m_track_n
            // calculating the offsets
            LOG.finer("Calculating chunk offsets for track_" + track.getTrackMetaData().getTrackId() + " chunk " + i);
            for (Track current : movie.getTracks()) {
                LOG.finest("Adding offsets of track_" + current.getTrackMetaData().getTrackId());
                long[] chunkSizes = getChunkSizes(current, movie);
                long firstSampleOfChunk = 0;
                for (int j = 0; j < i; j++) {
                    firstSampleOfChunk += chunkSizes[j];
                }
                if (current == track) {
                    chunkOffset[i] = offset;
                }

                for (long j = firstSampleOfChunk; j < firstSampleOfChunk + chunkSizes[i]; j++) {
                    if (j > Integer.MAX_VALUE) {
                        throw new InternalError("I cannot deal with a number of samples > Integer.MAX_VALUE");
                    }

                    offset += current.getSamples().get((int) j).getSize();
                }
            }
        }
        stco.setChunkOffsets(chunkOffset);
        stbl.addBox(stco);
        minf.addBox(stbl);
        mdia.addBox(minf);

        return trackBox;
    }

    private static class InterleaveChunkMdat implements Box {
        List<Track> tracks;
        LinkedList<Sample> samples = new LinkedList<Sample>();
        ContainerBox parent;
        
        public ContainerBox getParent() {
            return parent;
        }

        public void setParent(ContainerBox parent) {
            this.parent = parent;
        }

        public void parse(ReadableByteChannel inFC, ByteBuffer header, long contentSize, BoxParser boxParser) throws IOException {
        }

        private InterleaveChunkMdat(Movie movie) {
            
            tracks = movie.getTracks();
            Map<Track, long[]> chunks = new HashMap<Track, long[]>();
            for (Track track : movie.getTracks()) {
                chunks.put(track, getChunkSizes(track, movie));
            }

            for (int i = 0; i < chunks.values().iterator().next().length; i++) {
                for (Track track : tracks) {

                    long[] chunkSizes = chunks.get(track);
                    long firstSampleOfChunk = 0;
                    for (int j = 0; j < i; j++) {
                        firstSampleOfChunk += chunkSizes[j];
                    }

                    for (int j = l2i(firstSampleOfChunk); j < firstSampleOfChunk + chunkSizes[i]; j++) {

                        Sample s = track.getSamples().get(j);
                        Sample last = samples.peekLast();
                        if (s instanceof ByteArraySampleImpl && last instanceof ByteArraySampleImpl) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try {
                                baos.write(((ByteArraySampleImpl) last).data);
                                baos.write(((ByteArraySampleImpl) s).data);
                                ((ByteArraySampleImpl) last).data = baos.toByteArray();
                            } catch (IOException e) {
                                throw new RuntimeException("Should not happen. Even though ...", e);
                            }
                        } else if (s instanceof FileChannelSampleImpl && last instanceof FileChannelSampleImpl) {
                            FileChannelSampleImpl l = (FileChannelSampleImpl) last;
                            if ((l.fileChannel == ((FileChannelSampleImpl) s).fileChannel) && (l.offset + l.size == ((FileChannelSampleImpl) s).offset)) {
                                l.size += s.getSize();
                            } else {
                                samples.add(s);
                            }
                        } else {
                            samples.add(s);
                        }
                    }

                }

            }

        }

        public String getType() {
            return "mdat";
        }

        public long getSize() {
            long size = 8;
            for (Sample sample : samples) {
                size += sample.getSize();
            }
            return size;
            
        }

        public void getBox(WritableByteChannel writableByteChannel) throws IOException {
            ByteBuffer bb = ByteBuffer.allocate(8);
            IsoTypeWriter.writeUInt32(bb, getSize());
            bb.put(IsoFile.fourCCtoBytes("mdat"));
            for (Sample sample : samples) {
                if (sample instanceof ByteArraySampleImpl) {
                    writableByteChannel.write(ByteBuffer.wrap(((ByteArraySampleImpl) sample).data));
                } else if (sample instanceof FileChannelSampleImpl ) {
                    ((FileChannelSampleImpl) sample).fileChannel.transferTo(
                            ((FileChannelSampleImpl) sample).offset, 
                            ((FileChannelSampleImpl) sample).size,
                            writableByteChannel);
                    
                } else {
                    throw new RuntimeException("Don't know that kind of sample: " + sample.getClass().getSimpleName());
                }
            }
        }

    }

    /**
     * Gets the chunk sizes for the given track.
     *
     * @param track
     * @param movie
     * @return
     */
    static long[] getChunkSizes(Track track, Movie movie) {
        Track referenceTrack = null;
        long[] referenceChunkStarts = null;
        long referenceSampleCount = 0;
        long[] chunkSizes = null;
        for (Track test : movie.getTracks()) {
            if (test.getSyncSamples() != null && test.getSyncSamples().length > 0) {
                referenceTrack = test;
                referenceChunkStarts = test.getSyncSamples();
                referenceSampleCount = test.getSamples().size();
                chunkSizes = new long[referenceTrack.getSyncSamples().length];
            }

        }
        if (referenceTrack == null) {
            referenceTrack = movie.getTracks().get(0);
            referenceSampleCount = referenceTrack.getSamples().size();
            int chunkCount = (int) (Math.ceil(getDuration(referenceTrack) / referenceTrack.getTrackMetaData().getTimescale()) / 2);
            referenceChunkStarts = new long[chunkCount];
            long chunkSize = referenceTrack.getSamples().size() / chunkCount;
            for (int i = 0; i < referenceChunkStarts.length; i++) {
                referenceChunkStarts[i] = i * chunkSize;

            }

            chunkSizes = new long[chunkCount];
        }


        long sc = track.getSamples().size();
        // Since the number of sample differs per track enormously 25 fps vs Audio for example
        // we calculate the stretch. Stretch is the number of samples in current track that
        // are needed for the time one sample in reference track is presented.
        double stretch = (double) sc / referenceSampleCount;
        for (int i = 0; i < chunkSizes.length; i++) {
            long start = Math.round(stretch * ((referenceChunkStarts[i]) - 1));
            long end = 0;
            if (referenceChunkStarts.length == i + 1) {
                end = Math.round(stretch * (referenceSampleCount - 1));
            } else {
                end = Math.round(stretch * ((referenceChunkStarts[i + 1] - 1)));
            }

            chunkSizes[i] = end - start;
            // The Stretch makes sure that there are as much audio and video chunks!
        }
        assert track.getSamples().size() == sum(chunkSizes) : "The number of samples and the sum of all chunk lengths must be equal";
        return chunkSizes;


    }


    private static long sum(long[] ls) {
        long rc = 0;
        for (long l : ls) {
            rc += l;
        }
        return rc;
    }

    protected static long getDuration(Track track) {
        long duration = 0;
        for (TimeToSampleBox.Entry entry : track.getDecodingTimeEntries()) {
            duration += entry.getCount() * entry.getDelta();
        }
        return duration;
    }

    public long getTimescale(Movie movie) {
        long timescale = movie.getTracks().iterator().next().getTrackMetaData().getTimescale();
        for (Track track : movie.getTracks()) {
            timescale = gcd(track.getTrackMetaData().getTimescale(), timescale);
        }
        return timescale;
    }

    public static long gcd(long a, long b) {
        if (b == 0) {
            return a;
        }
        return gcd(b, a % b);
    }
}
