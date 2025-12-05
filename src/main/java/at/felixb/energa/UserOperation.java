package at.felixb.energa;

import java.util.List;

public interface UserOperation<T extends CrdtOperation> {
   List<T> transformToInternal(Document document);
}
