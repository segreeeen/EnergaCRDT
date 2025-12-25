package at.felixb.energa.presence;

import java.util.List;

public record ResolvedPresence(String sessionId, long seq, String metaBlob, List<Resolved> resolutions) {

}
