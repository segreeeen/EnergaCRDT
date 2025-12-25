package at.felixb.energa.presence;

import java.util.ArrayList;
import java.util.List;

public class EphemeralState {
    private List<Resolvable> resolvables = new ArrayList<>();

    public List<Resolvable> getResolvables() {
        return resolvables;
    }

    public void setResolvables(List<Resolvable> resolvables) {
        this.resolvables = resolvables;
    }
}
