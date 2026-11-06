package com.lms.files.internal;

import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.mediaconvert.model.AacCodingMode;
import software.amazon.awssdk.services.mediaconvert.model.AacSettings;
import software.amazon.awssdk.services.mediaconvert.model.AudioCodec;
import software.amazon.awssdk.services.mediaconvert.model.AudioCodecSettings;
import software.amazon.awssdk.services.mediaconvert.model.AudioDefaultSelection;
import software.amazon.awssdk.services.mediaconvert.model.AudioDescription;
import software.amazon.awssdk.services.mediaconvert.model.AudioSelector;
import software.amazon.awssdk.services.mediaconvert.model.ContainerSettings;
import software.amazon.awssdk.services.mediaconvert.model.ContainerType;
import software.amazon.awssdk.services.mediaconvert.model.FileGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.FrameCaptureSettings;
import software.amazon.awssdk.services.mediaconvert.model.H264QvbrSettings;
import software.amazon.awssdk.services.mediaconvert.model.H264RateControlMode;
import software.amazon.awssdk.services.mediaconvert.model.H264SceneChangeDetect;
import software.amazon.awssdk.services.mediaconvert.model.H264Settings;
import software.amazon.awssdk.services.mediaconvert.model.HlsGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.Input;
import software.amazon.awssdk.services.mediaconvert.model.InputTimecodeSource;
import software.amazon.awssdk.services.mediaconvert.model.JobSettings;
import software.amazon.awssdk.services.mediaconvert.model.Output;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroup;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroupSettings;
import software.amazon.awssdk.services.mediaconvert.model.OutputGroupType;
import software.amazon.awssdk.services.mediaconvert.model.VideoCodec;
import software.amazon.awssdk.services.mediaconvert.model.VideoCodecSettings;
import software.amazon.awssdk.services.mediaconvert.model.VideoDescription;
import software.amazon.awssdk.services.mediaconvert.model.VideoSelector;

/**
 * MediaConvert job: the uploaded source → adaptive HLS (1080p / 720p / 480p, 6-second segments)
 * plus one thumbnail. Why transcode at all: any phone/browser plays H.264 HLS, the player switches
 * quality with the viewer's bandwidth (ABR), and a 480p rendition costs a fraction of the traffic.
 * QVBR keeps quality constant and spends bits only where the picture needs them.
 */
final class HlsJobSettings {

    /** name modifier, width, height, max bitrate. */
    record Rendition(String suffix, int width, int height, int maxBitrate) {
    }

    static final List<Rendition> LADDER = List.of(
            new Rendition("_1080p", 1920, 1080, 6_000_000),
            new Rendition("_720p", 1280, 720, 3_500_000),
            new Rendition("_480p", 854, 480, 1_500_000));

    static final int SEGMENT_SECONDS = 6;

    private HlsJobSettings() {
    }

    /** {@code tenants/{t}/videos/{id}/hls/} → master playlist {@code index.m3u8} in that folder. */
    static String renditionPrefix(String tenantId, String videoId) {
        return "tenants/" + tenantId + "/videos/" + videoId + "/hls/";
    }

    static String manifestKey(String tenantId, String videoId) {
        return renditionPrefix(tenantId, videoId) + "index.m3u8";
    }

    static JobSettings build(String bucket, String sourceKey, String tenantId, String videoId) {
        String destination = "s3://" + bucket + "/" + renditionPrefix(tenantId, videoId);
        Input input = Input.builder()
                .fileInput("s3://" + bucket + "/" + sourceKey)
                .timecodeSource(InputTimecodeSource.ZEROBASED)
                .videoSelector(VideoSelector.builder().build())
                .audioSelectors(Map.of("Audio Selector 1",
                        AudioSelector.builder().defaultSelection(AudioDefaultSelection.DEFAULT).build()))
                .build();

        OutputGroup hls = OutputGroup.builder()
                .name("HLS")
                .outputGroupSettings(OutputGroupSettings.builder()
                        .type(OutputGroupType.HLS_GROUP_SETTINGS)
                        .hlsGroupSettings(HlsGroupSettings.builder()
                                .destination(destination + "index")
                                .segmentLength(SEGMENT_SECONDS)
                                .minSegmentLength(0)
                                .build())
                        .build())
                .outputs(LADDER.stream().map(HlsJobSettings::output).toList())
                .build();

        OutputGroup thumbnail = OutputGroup.builder()
                .name("Thumbnail")
                .outputGroupSettings(OutputGroupSettings.builder()
                        .type(OutputGroupType.FILE_GROUP_SETTINGS)
                        .fileGroupSettings(FileGroupSettings.builder().destination(destination + "thumbnail").build())
                        .build())
                .outputs(Output.builder()
                        .containerSettings(ContainerSettings.builder().container(ContainerType.RAW).build())
                        .videoDescription(VideoDescription.builder().width(640).height(360)
                                .codecSettings(VideoCodecSettings.builder()
                                        .codec(VideoCodec.FRAME_CAPTURE)
                                        .frameCaptureSettings(FrameCaptureSettings.builder()
                                                .framerateNumerator(1).framerateDenominator(10).maxCaptures(1).quality(80)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        return JobSettings.builder().inputs(input).outputGroups(hls, thumbnail).build();
    }

    private static Output output(Rendition r) {
        return Output.builder()
                .nameModifier(r.suffix())
                .containerSettings(ContainerSettings.builder().container(ContainerType.M3_U8).build())
                .videoDescription(VideoDescription.builder()
                        .width(r.width()).height(r.height())
                        .codecSettings(VideoCodecSettings.builder()
                                .codec(VideoCodec.H_264)
                                .h264Settings(H264Settings.builder()
                                        .rateControlMode(H264RateControlMode.QVBR)
                                        .qvbrSettings(H264QvbrSettings.builder().qvbrQualityLevel(7).build())
                                        .maxBitrate(r.maxBitrate())
                                        .sceneChangeDetect(H264SceneChangeDetect.TRANSITION_DETECTION)
                                        .build())
                                .build())
                        .build())
                .audioDescriptions(AudioDescription.builder()
                        .codecSettings(AudioCodecSettings.builder()
                                .codec(AudioCodec.AAC)
                                .aacSettings(AacSettings.builder()
                                        .bitrate(96_000).codingMode(AacCodingMode.CODING_MODE_2_0).sampleRate(48_000)
                                        .build())
                                .build())
                        .build())
                .build();
    }
}
