package com.order.menu.module.entertainment.mode.naval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persisted private state; clients receive only NavalEngine.view(). */
public class NavalState {
    public int rulesVersion;
    public String id;
    public String inviteCode;
    public String mode;
    public String ruleSet = "CLASSIC";
    public boolean autoMatch;
    public boolean allowSpectators = true;
    public boolean localSimulation;
    public int lobbySharedRound;
    public String phase = "WAITING";
    public long deadline;
    public boolean presentationSync;
    public boolean connectionTracking;
    public boolean battleStarted;
    public long presentationUntil;
    public long presentationId;
    public List<Integer> presentationReady = new ArrayList<>();
    public long lastSeenAt;
    public long nextAiAt;
    public long finishedAt;
    public int turn;
    public int mineActions;
    public List<Action> aiTemplate = new ArrayList<>();
    public Integer winner;
    public long sequence;
    public int round = 1;
    public String finishReason;
    public Map<String, Object> settlement;
    public List<Player> players = new ArrayList<>();
    public List<Spectator> spectators = new ArrayList<>();
    public List<Message> messages = new ArrayList<>();
    public List<Event> events = new ArrayList<>();

    public static class Player {
        public Map<String,Object> medals;
        public String id = java.util.UUID.randomUUID().toString();
        public String key;
        public String name;
        public String avatarUrl;
        public boolean registered;
        public boolean left;
        public long lastSeenAt;
        public long disconnectedAt;
        public String connectionToken;
        public boolean continueRequested;
        public String stage = "PLACEMENT";
        public boolean ready;
        public List<Ship> ships = new ArrayList<>();
        public List<Shot> shots = new ArrayList<>();
        public List<Cell> mines = new ArrayList<>();
        public List<Integer> aa = new ArrayList<>();
        public Map<String, Integer> inventory = NavalEngine.emptyInventory();
        public int coins = 300;
        public List<Radar> radar = new ArrayList<>();
    }

    public static class Spectator {
        public Map<String,Object> medals;
        public String id;
        public String key;
        public String name;
        public String avatarUrl;
        public boolean registered;
        public String viewMode = "BOTH";
        public long lastSeenAt;
    }

    public static class Ship {
        public String id;
        public String kind;
        public int minesUsed;
        public int rotation;
        public List<Integer> lostPlanes = new ArrayList<>();
        public int x;
        public int y;
        public int w;
        public int h;
        public boolean stealth;
        public List<String> hits = new ArrayList<>();
        public Ship() { }
        public Ship(String id, int x, int y, int w, int h) {
            this.id = id; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    public record Cell(int x, int y) { }
    public record Shot(int x, int y, String result) { }
    public record Radar(int x, int y, boolean occupied) { }
    public record Message(long id, int player, String text, long at, String name, String senderId) {
        public Message(long id, int player, String text, long at) {
            this(id,player,text,at,null,null);
        }
    }
    public record Event(long id, int player, String weapon, List<Shot> cells, String text,
                        Integer x, Integer y, Integer row, List<Cell> targets, List<Cell> route,
                        List<List<Cell>> torpedoes, boolean destroyed, List<Shot> backfire, String shipId, int plane) {
        public Event(long id, int player, String weapon, List<Shot> cells, String text, Integer x, Integer y, Integer row, List<Cell> targets, List<Cell> route, List<List<Cell>> torpedoes, boolean destroyed, List<Shot> backfire) {
            this(id,player,weapon,cells,text,x,y,row,targets,route,torpedoes,destroyed,backfire,null,0);
        }
        public Event(long id, int player, String weapon, List<Shot> cells, String text, Integer x, Integer y, Integer row, List<Cell> targets, List<Cell> route, List<List<Cell>> torpedoes, boolean destroyed) {
            this(id,player,weapon,cells,text,x,y,row,targets,route,torpedoes,destroyed,List.of());
        }
        public Event(long id, int player, String weapon, List<Shot> cells, String text, Integer x, Integer y, Integer row) {
            this(id,player,weapon,cells,text,x,y,row,List.of(),List.of(),List.of(),false);
        }
        public Event(long id, int player, String weapon, List<Shot> cells, String text) {
            this(id, player, weapon, cells, text, null, null, null);
        }
    }

    public static class Action {
        public String type;
        public List<Ship> ships;
        public List<Cell> mines;
        public List<Integer> aa;
        public List<String> coatedShips;
        public List<Cell> targets;
        public Map<String, Integer> inventory;
        public boolean ready;
        public String weapon;
        public String shipId;
        public int plane;
        public int x;
        public int y;
        public String text;
        public String viewMode;
        public Integer seat;
        public Boolean value;
        public long presentationId;
    }
}
