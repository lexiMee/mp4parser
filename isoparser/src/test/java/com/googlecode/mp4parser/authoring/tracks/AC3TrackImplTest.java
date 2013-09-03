package com.googlecode.mp4parser.authoring.tracks;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.IOException;

public class AC3TrackImplTest {
    @Test
    public void freeze() throws IOException {
        Track t = new AC3TrackImpl(new BufferedInputStream(AC3TrackImpl.class.getResourceAsStream("/com/googlecode/mp4parser/authoring/tracks/ac3-sample.ac3")));
        Movie m = new Movie();
        m.addTrack(t);

        DefaultMp4Builder mp4Builder = new DefaultMp4Builder();
        Container isoFile = mp4Builder.build(m);
        //DataSource fc = new FileOutputStream("c:/dev/mp4parser/isoparser/src/test/resources/com/googlecode/mp4parser/authoring/tracks/ac3-sample-new.mp4").getChannel();
        //isoFile.writeContainer(fc);
        //fc.close();
        IsoFile isoFileReference = new IsoFile(this.getClass().getProtectionDomain().getCodeSource().getLocation().getFile() + "/com/googlecode/mp4parser/authoring/tracks/ac3-sample.mp4");
        BoxComparator.check(isoFile, isoFileReference, "/moov[0]/mvhd[0]", "/moov[0]/trak[0]/tkhd[0]", "/moov[0]/trak[0]/mdia[0]/mdhd[0]");
    }
}
