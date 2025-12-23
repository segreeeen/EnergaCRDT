package at.felixb.energa.presence;

import java.util.List;

public record ResolvedPresence(String sessionId, long metaSeq, String metaBlob, long ephemeralSeq, List<Resolved> resolutions) {

}
