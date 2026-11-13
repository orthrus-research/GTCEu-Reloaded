package gregtech.api.util;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class PositionedRectTest {

    private static final PositionedRect BOX = new PositionedRect(10, 10, 20, 20);

    @Test
    public void Test_Contained_Rect_Intersects_Both_Ways() {
        PositionedRect inner = new PositionedRect(15, 15, 5, 5);
        assertThat(BOX.intersects(inner), is(true));
        assertThat(inner.intersects(BOX), is(true));
    }

    @Test
    public void Test_Cross_Shaped_Overlap_Intersects() {
        PositionedRect bar = new PositionedRect(0, 15, 50, 5);
        assertThat(BOX.intersects(bar), is(true));
        assertThat(bar.intersects(BOX), is(true));
    }

    @Test
    public void Test_Separate_Rects_Do_Not_Intersect() {
        assertThat(BOX.intersects(new PositionedRect(40, 10, 5, 5)), is(false));
        assertThat(BOX.intersects(new PositionedRect(10, 40, 5, 5)), is(false));
    }

    @Test
    public void Test_Corner_Overlap_Intersects() {
        assertThat(BOX.intersects(new PositionedRect(25, 25, 10, 10)), is(true));
    }
}
