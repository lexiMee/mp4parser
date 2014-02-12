package com.googlecode.mp4parser.muxformats;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.tracks.DTSTrackImpl;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Created by sannies on 2/12/14.
 */
public class DTSMuxExample {
    public static void main(String[] args) throws IOException {
        Movie movie = new Movie();
        Track track = new DTSTrackImpl(new FileDataSourceImpl("C:\\dev\\cff-creator\\src\\test\\resources\\RIO_Trailer_DTSExpress_256kbps.dtshd"));
        movie.addTrack(track);

        DefaultMp4Builder builder = new DefaultMp4Builder();
        Container container = builder.build(movie);
        FileOutputStream fos = new FileOutputStream("output.mp4");
        FileChannel fc = fos.getChannel();
        container.writeContainer(fc);
        fos.close();
    }
}
