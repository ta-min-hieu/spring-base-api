package com.ringme.base.service.common.impl;

import com.ringme.base.config.app.AppConfig;
import com.ringme.base.service.common.MediaService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;

@Log4j2
@Service
public class MediaServiceImpl implements MediaService {
    @Autowired
    private AppConfig cf;

    @Override
    public String checkImgDefault(String imgPath) {
        if (imgPath == null || imgPath.isBlank() || imgPath.endsWith("null")) {
            return Paths.get(cf.getImgDefault()).toString();
        }
        return imgPath;
    }

    @Override
    public String checkAudioDefault(String audioPath) {
        if (audioPath == null || audioPath.isBlank() || audioPath.endsWith("null")) {
            return Paths.get(cf.getAudioDefault()).toString();
        }
        return audioPath;
    }

    @Override
    public String checkAvatarDefault(String imgPath) {
        if (imgPath == null || imgPath.isBlank() || imgPath.endsWith("null")) {
            return Paths.get(cf.getImgDefault()).toString();
        }
        return imgPath;
    }

    @Override
    public String getMedia(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return cf.getDomainCdnMedia() + path;
    }

    @Override
    public String getAvatar(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        return cf.getDomainCdnMedia() + "/timor158" + path;
    }
}
