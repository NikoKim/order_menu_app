package com.navy.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persisted private state; clients receive only NavalEngine.view(). */
public class NavalState {
    public int shotMarkersVersion;
    @com.fasterxml.jackson.annotation.JsonIgnore public String persistedJson;
    public long version = 1;
    public int rulesVersion;
    public String id;
    public String matchedRoomId;
    public String inviteCode;
    public String mode;
    public String ruleSet = "CLASSIC";
    public int wager;
    public int wagerPot;
    public int nextWager;
    public int wagerRound;
    public boolean autoMatch;
    public boolean allowSpectators = true;
    public long spectatorChangedAt;
    public int spectatorChangedBy = -1;
    public String spectatorChangedByName;
    public int spectatorChangedRound;
    public long spectatorCooldownUntil;
    public long spectatorSettingsRevision;
    public boolean taskSpectatorsOpened;
    // Legacy room JSON has no proof that spectators stayed disabled throughout battle.
    public int taskEligibilityVersion;
    public boolean localSimulation;
    public int lobbySharedRound;
    public String phase = "WAITING";
    public long deadline;
    public long battlePausedAt;
    public Map<String,Object> localPause;
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
    public int supportActions;
    public String supportShipId;
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
        public Map<String,Object> progression;
        public String id = java.util.UUID.randomUUID().toString();
        public String key;
        public String name;
        public String avatarUrl;
        public boolean registered;
        public int wager;
        public Integer selectedWager;
        public int nextWager;
        public boolean canSkipWager;
        public boolean left;
        public long lastSeenAt;
        public long disconnectedAt;
        public String connectionToken;
        public boolean reconnectRequired;
        public int reconnectCount;
        public List<String> retiredConnectionTokens = new ArrayList<>();
        public boolean continueRequested;
        public String stage = "PLACEMENT";
        public boolean ready;
        public List<Ship> ships = new ArrayList<>();
        public List<Shot> shots = new ArrayList<>();
        public List<Cell> mines = new ArrayList<>();
        public List<Cell> scrapMines = new ArrayList<>();
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
    public record Shot(int x, int y, String result,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String mineKind,
                       @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Boolean uncertain) {
        public Shot(int x,int y,String result) { this(x,y,result,null,null); }
        public Shot(int x,int y,String result,String mineKind) { this(x,y,result,mineKind,null); }
    }
    public record Radar(int x, int y, boolean occupied) { }
    public record Message(long id, int player, String text, long at, String name, String senderId,String bubbleStyle,long bubbleExpiresAt) {
        public Message(long id,int player,String text,long at,String name,String senderId){this(id,player,text,at,name,senderId,null,0);}
        public Message(long id, int player, String text, long at) {
            this(id,player,text,at,null,null);
        }
        public Message visible(long now){return bubbleStyle!=null&&bubbleExpiresAt<=now?new Message(id,player,text,at,name,senderId):this;}
    }
    public record Event(long id, int player, String weapon, List<Shot> cells, String text,
                        Integer x, Integer y, Integer row, List<Cell> targets, List<Cell> route,
                        List<List<Cell>> torpedoes, boolean destroyed, List<Shot> backfire, String shipId, int plane, Map<String,Object> support) {
        public Event(long id,int player,String weapon,List<Shot> cells,String text,Integer x,Integer y,Integer row,List<Cell> targets,List<Cell> route,List<List<Cell>> torpedoes,boolean destroyed,List<Shot> backfire,String shipId,int plane){this(id,player,weapon,cells,text,x,y,row,targets,route,torpedoes,destroyed,backfire,shipId,plane,null);}
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
        public Integer wager;
        public String requestId;
        @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
        public String pageToken;
        public String type;
        public List<Ship> ships;
        public List<Cell> mines;
        public List<Integer> aa;
        public List<String> coatedShips;
        public List<Cell> targets;
        public Map<String, Integer> inventory;
        public boolean ready;
        public String weapon;
        public String supportMode;
        public String targetShipId;
        public Integer rotation;
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
