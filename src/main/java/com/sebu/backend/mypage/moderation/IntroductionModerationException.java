package com.sebu.backend.mypage.moderation;

public class IntroductionModerationException extends RuntimeException {

    public IntroductionModerationException() {
        super("INTRODUCTION_POLICY_VIOLATION");
    }

}
