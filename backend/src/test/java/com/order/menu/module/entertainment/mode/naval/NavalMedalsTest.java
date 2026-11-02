package com.order.menu.module.entertainment.mode.naval;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class NavalMedalsTest {
 @Test void thresholdsAndModeSeparation(){assertTrue(NavalMedals.unlocked(2,2).isEmpty());assertEquals(List.of("classic-3"),NavalMedals.unlocked(3,0));assertEquals(4,NavalMedals.unlocked(10,3).size());assertEquals(6,NavalMedals.unlocked(10,10).size());assertEquals(0,NavalMedals.next(9,false));assertEquals(10,NavalMedals.next(9,true));}
}
