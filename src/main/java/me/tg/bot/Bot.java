package me.tg.bot;

import com.sun.tools.corba.se.idl.InterfaceGen;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.api.methods.BotApiMethod;
import org.telegram.telegrambots.api.methods.groupadministration.KickChatMember;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Contact;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.User;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Andreyko0 on 01/11/2017.
 */
public class Bot extends TelegramLongPollingBot {

  private final String TOKEN;

  public Bot(String token) {
    TOKEN = token;
  }

  private class Vote {
    Map<Integer, Integer> vs = new HashMap<>();
    User user;
    int min = 10;
    int votes = 0;

    synchronized void setMin(int m) {
      this.min = m;
    }

    synchronized boolean voteFor(Integer id) {
      vs.put(id, true);
      return isMin();
    }

    synchronized boolean isMin() {
      int plus = 0;
      int minus = 0;
      for (Boolean b : vs.values()) {
        if (b) {
          ++plus;
        } else {
          ++minus;
        }
      }
      return plus-minus>=min;
    }
  }

  private ConcurrentHashMap<Long, Vote> votes = new ConcurrentHashMap<>();

  @Override
  public void onUpdateReceived(Update update) {
    Message msg = update.getMessage();
    if (msg == null) {
      return;
    }
    Long chatId = msg.getChatId();
    if (!votes.containsKey(chatId)) {
      Vote v = new Vote();
      votes.put(chatId, v);
      send( String.format("created new vote by default, min: %d", v.min), chatId);
    }
    if (msg.isCommand()) {
      if (msg.getText().startsWith("/ban")) {
        if (!msg.isReply()) {
          send("Should be a reply", chatId);
          return;
        }

        User user = msg.getReplyToMessage().getFrom();
//        votes.put(chatId, new Vote(user));
        Vote v = votes.get(chatId);
        if (v == null) {
          noVote(chatId);
          return;
        }
        v.setUser(user);
        send("Ban @"+user.getUserName()+"? send '+' or '-'", chatId);
        return;
      }
      if (msg.getText().startsWith("/res")) {
        Vote v = votes.get(chatId);
        if (v.user == null) {
          send("No one to ban", chatId);
          return;
        }
        int plus = 0;
        int minus = 0;
        for (Boolean b : v.vs.values()) {
          if (b) {
            ++plus;
          } else {
            ++minus;
          }
        }
        String s = String.format("Ban @%s (%s %s)?\n+ %d\n- %d\ndiff: %d", v.user.getUserName(), v.user.getFirstName(), v.user.getLastName(), plus, minus, plus-minus);
        send(s, chatId);
        return;
      }

      if (msg.getText().startsWith("/create")) {
        votes.put(chatId, new Vote());
        send("Created vote", chatId);
        return;
      }

      if (msg.getText().startsWith("/min")) {
        Vote v = votes.get(chatId);
        if (v == null) {
          noVote(chatId);
          return;
        }
        int i = Integer.valueOf(msg.getText().substring(5));
        v.setMin(i);
        send(String.format("Set min to %d", i), chatId);
        return;
      }
    }
    Integer uId = msg.getFrom().getId();
    if (msg.getText().equals("+")) {
      Vote v = votes.get(chatId);
      if (v.user == null) {
        send("No one to ban", chatId);
        return;
      }
      if (v.voteFor(uId)) {
        if (v.hasUser()) {
          KickChatMember kick = new KickChatMember(chatId, v.user.getId());
          try {
            execute(kick);
          } catch (TelegramApiException e) {
            send("Tried to ban/kick @" + v.user.getUserName() +"("+v.user.getFirstName()+" "+ v.user.getLastName() + "), didn't work. Dafuq: "+e.getMessage(), chatId);
          }
        } else {
          send("@" + v.user.getUserName() +"("+v.user.getFirstName()+" "+ v.user.getLastName() + ") is not in the vote. Dafuq", chatId);
        }
        return;
      }
    }
    if (msg.getText().equals("-")) {
      Vote v = votes.get(chatId);
      if (v.user == null) {
        send("No one to ban", chatId);
        return;
      }
      v.voteAgainst(uId);
    }
  }

  private void send(String text, Long chatId) {
    SendMessage msg = new SendMessage();
    msg.setChatId(chatId);
    msg.setText(text);
    try {
      execute(msg);
    } catch (TelegramApiException e) {
      System.out.println("Ex"+e.getMessage());
    }
  }

  private void noVote(Long chatID) {
    SendMessage msg = new SendMessage();
    msg.setChatId(chatID);
    msg.setText("There is no vote");
    try {
      execute(msg);
    } catch (TelegramApiException e) {
      System.out.println("Ex"+e.getMessage());
    }
  }

  @Override
  public String getBotUsername() {
    return "PMBot";
  }

  @Override
  public String getBotToken() {
    return TOKEN;
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.println("Provide a token for bot");
    }
    ApiContextInitializer.init();
    TelegramBotsApi api = new TelegramBotsApi();
    try {
      api.registerBot(new Bot(args[0]));
    } catch (Exception e) {
      System.out.println("ex"+ e.getMessage());
    }
  }
}
