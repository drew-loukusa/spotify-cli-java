package spotifyCliJava.utility;

import java.time.LocalDateTime;

public class GenericCredentials {
    private final String accessToken;
    private final String refreshToken;
    private final Integer expiresIn;
    private final String accessCreationTimeStamp;

    private GenericCredentials(Builder builder) {
        this.accessToken = builder.accessToken;
        this.refreshToken = builder.refreshToken;
        this.expiresIn = builder.expiresIn;
        this.accessCreationTimeStamp = builder.accessCreationTimeStamp;
    }

    public static String getTimeStamp() {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        int day = now.getDayOfMonth();
        int hour = now.getHour();
        int minute = now.getMinute();
        int second = now.getSecond();
        return String.format("%d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, minute, second);
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public String getAccessCreationTimeStamp() {
        return accessCreationTimeStamp;
    }

    public static class Builder {

        private String accessToken;
        private String refreshToken;
        private Integer expiresIn;
        private String accessCreationTimeStamp = getTimeStamp();

        public Builder withAccessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder withRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder withExpiresIn(Integer expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        /**
         * NOTE: accessCreationTimeStamp is AUTOMATICALLY set by the class when a spotifyCliJava.utility.GenericCredentials object is created.
         * You can override with your own timestamp, but it has to be in the following format:
         * FORMAT: %d-%02d-%02d %02d:%02d:%02d
         * EXAMPLE DATE: 2021-10-29 14:02:16
         */
        public Builder withAccessCreationTimeStamp(String accessCreationTimeStamp) {
            // TODO: validate format of string here, raise exception?
            this.accessCreationTimeStamp = accessCreationTimeStamp;
            return this;
        }

        public GenericCredentials build() {
            return new GenericCredentials(this);
        }
    }
}