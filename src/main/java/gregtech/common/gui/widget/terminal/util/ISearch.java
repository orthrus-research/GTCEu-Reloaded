package gregtech.common.gui.widget.terminal.util;

import java.util.function.Consumer;

public interface ISearch<T> {

    /** Long-running searches should observe interruption; cancelled searches cannot publish into later queries. */
    void search(String word, Consumer<T> find);
}
