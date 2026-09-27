package com.rentbook.condition;

import com.rentbook.property.Unit;

import java.util.List;

/** The lines a new move-in report starts with, by what is being let. The landlord adds and removes from there. */
final class Checklists {

    record Line(String area, String item) {
    }

    private Checklists() {
    }

    static List<Line> forUnit(Unit.Kind kind) {
        return switch (kind) {
            case FLAT -> List.of(
                    new Line("Entrance", "Main door and lock"),
                    new Line("Living room", "Walls and paint"),
                    new Line("Living room", "Floor"),
                    new Line("Living room", "Windows and grilles"),
                    new Line("Living room", "Fans and lights"),
                    new Line("Living room", "Switches and sockets"),
                    new Line("Kitchen", "Counter and sink"),
                    new Line("Kitchen", "Taps and plumbing"),
                    new Line("Kitchen", "Cabinets"),
                    new Line("Kitchen", "Chimney or exhaust"),
                    new Line("Bedroom", "Walls and paint"),
                    new Line("Bedroom", "Floor"),
                    new Line("Bedroom", "Wardrobe"),
                    new Line("Bedroom", "Fan and lights"),
                    new Line("Bathroom", "Taps and shower"),
                    new Line("Bathroom", "Toilet and flush"),
                    new Line("Bathroom", "Geyser"),
                    new Line("Bathroom", "Tiles"),
                    new Line("Balcony", "Floor and railing"),
                    new Line("Keys", "Keys handed over"));
            case ROOM -> List.of(
                    new Line("Room", "Door and lock"),
                    new Line("Room", "Walls and paint"),
                    new Line("Room", "Floor"),
                    new Line("Room", "Windows"),
                    new Line("Room", "Fan and lights"),
                    new Line("Room", "Switches and sockets"),
                    new Line("Room", "Wardrobe"),
                    new Line("Room", "Bed and mattress"),
                    new Line("Bathroom", "Taps and shower"),
                    new Line("Bathroom", "Toilet and flush"),
                    new Line("Bathroom", "Tiles"),
                    new Line("Keys", "Keys handed over"));
            case BED -> List.of(
                    new Line("Bed", "Bed and mattress"),
                    new Line("Bed", "Pillow and linen"),
                    new Line("Bed", "Locker"),
                    new Line("Bed", "Study table and chair"),
                    new Line("Room", "Fan and lights"),
                    new Line("Room", "Switches and sockets"),
                    new Line("Keys", "Room and locker keys"));
        };
    }
}
