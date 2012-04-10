package com.googlecode.mp4parser.authoring.tracks;

import com.coremedia.iso.boxes.TimeToSampleBox;
import com.googlecode.mp4parser.authoring.InTestMovieCreator;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import junit.framework.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static com.googlecode.mp4parser.util.Math.lcm;

public class ChangeTimeScaleTrackTest {
    @Test
    public void testThirdFrameRateAndSampleDurations() throws IOException {
        Movie m = InTestMovieCreator.createMovieOnlyVideo(
                "/scene_cut_on/FBW_fixedres_B_640x360_200.mp4",
                "/scene_cut_on/FBW_fixedres_B_640x360_400.mp4",
                "/scene_cut_on/FBW_fixedres_B_640x360_800.mp4",
                "/scene_cut_on/FBW_fixedres_B_640x360_1200.mp4");

        Movie timeScaledTrack = new Movie();
        List<Long> showTimes = null;
        for (Track track : m.getTracks()) {
            if (showTimes == null) {
                showTimes = calcSampleShowTimes(track, m);
            } else {
                showTimes.retainAll(calcSampleShowTimes(track, m));
            }
            long scale = ChangeTimeScaleTrack.getGoodScaleFactor(track, m, 10000000);
            timeScaledTrack.addTrack(new ChangeTimeScaleTrack(track, 10000000, scale));
        }
        int numCommonSamples = showTimes.size();
        showTimes = null;
        for (Track track : timeScaledTrack.getTracks()) {
            if (showTimes == null) {
                showTimes = calcSampleShowTimes(track, m);
            } else {
                showTimes.retainAll(calcSampleShowTimes(track, m));
            }
        }

        Assert.assertEquals(numCommonSamples, showTimes.size());

    }

    public static List<Long> calcSampleShowTimes(Track track, Movie m) {
        LinkedList<Long> sampleShowTime = new LinkedList<Long>();
        long duration = 0;
        long left = 0;
        long currentDelta = 0;
        LinkedList<TimeToSampleBox.Entry> timeQueue = new LinkedList<TimeToSampleBox.Entry>(track.getDecodingTimeEntries());
        long timeScale = 1;
        for (Track track1 : m.getTracks()) {
            if (track1.getTrackMetaData().getTimescale() != track.getTrackMetaData().getTimescale()) {
                timeScale = lcm(timeScale, track1.getTrackMetaData().getTimescale());
            }
        }
        while (left > 0 || !timeQueue.isEmpty()) {
            if (left-- == 0 && !timeQueue.isEmpty()) {
                TimeToSampleBox.Entry entry = timeQueue.poll();
                left = entry.getCount();
                currentDelta = entry.getDelta();
            }
            sampleShowTime.add(duration);
            duration += currentDelta;
        }
        return sampleShowTime;
    }
}
