package com.ringme.base.service.common;

public interface MediaService {
    String checkImgDefault(String imgUrl);

    String checkAudioDefault(String audioUrl);

    String checkAvatarDefault(String imgPath);

    String getMedia(String path);

    String getAvatar(String path);
}
