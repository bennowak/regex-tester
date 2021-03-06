package org.ocpsoft.tutorial.regex.server;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.context.RequestScoped;

import org.jboss.errai.bus.server.annotations.Service;
import org.ocpsoft.tutorial.regex.client.shared.Group;
import org.ocpsoft.tutorial.regex.client.shared.ParseTools;
import org.ocpsoft.tutorial.regex.client.shared.ParseTools.CaptureFilter;
import org.ocpsoft.tutorial.regex.client.shared.ParseTools.CaptureType;
import org.ocpsoft.tutorial.regex.client.shared.ParseTools.CapturingGroup;
import org.ocpsoft.tutorial.regex.client.shared.RegexException;
import org.ocpsoft.tutorial.regex.client.shared.RegexParser;
import org.ocpsoft.tutorial.regex.client.shared.RegexRequest;
import org.ocpsoft.tutorial.regex.client.shared.RegexResult;

@Service
@RequestScoped
public class RegexParserImpl implements RegexParser
{
   private static final long REGEX_TIMOUT_MS = 5000;

   @Override
   public RegexResult parse(final RegexRequest request)
   {
      final RegexResult result = new RegexResult();

      Runnable runnable = new Runnable() {
         @Override
         public void run()
         {
            result.setText(request.getText());

            CharSequence text = new InterruptibleCharSequence(request.getText());
            Matcher matcher = null;

            String replacement = request.getReplacement();
            String regex = request.getRegex();

            if (text != null && text.length() > 0)
            {
               try {
                  if (regex != null)
                  {
                     matcher = Pattern.compile(regex).matcher(text);
                  }
               }
               catch (Exception e) {
                  result.setError(e.getMessage());
               }

               List<CapturingGroup> fragments = ParseTools.extractCaptures(CaptureType.PAREN, regex,
                        new CaptureFilter() {
                           @Override
                           public boolean accept(CapturingGroup group)
                           {
                              String captured = new String(group.getCaptured());
                              return !captured.startsWith("?");
                           }
                        });

               if (matcher != null)
               {
                  matcher.reset();
                  if (matcher.matches())
                  {
                     result.setMatches(matcher.matches());
                     result.getGroups().clear();
                     for (int i = 0; i < matcher.groupCount(); i++)
                     {
                        int start = matcher.start(i + 1);
                        int end = matcher.end(i + 1);
                        if (start != -1 && end != -1)
                           result.getGroups().add(new Group(fragments.get(i), start, end));
                     }

                     StringBuffer replaced = new StringBuffer();
                     if (replacement != null && !replacement.isEmpty()) {
                        matcher.appendReplacement(replaced, request.getReplacement());
                        result.setReplaced(replaced.toString());
                     }
                  }
                  else
                  {
                     matcher.reset();
                     while (matcher.find())
                     {
                        Group defaultGroup = new Group(
                                 new CapturingGroup(("(" + regex + ")").toCharArray(), 0, regex.length() + 1),
                                 matcher.start(),
                                 matcher.end());
                        result.getGroups().add(defaultGroup);
                        for (int i = 0; i < matcher.groupCount(); i++)
                        {
                           int start = matcher.start(i + 1);
                           int end = matcher.end(i + 1);
                           if ((start != -1 && end != -1) &&
                                    (defaultGroup.getStart() != start || defaultGroup.getEnd() != end))
                           {
                              result.getGroups().add(new Group(fragments.get(i), start, end));
                           }
                        }
                     }

                     if (replacement != null && !replacement.isEmpty())
                        result.setReplaced(matcher.replaceAll(replacement));
                  }
               }
            }
         }
      };

      long startTime = System.currentTimeMillis();

      Thread thread = new Thread(runnable);
      thread.start();

      try {
         while (thread.isAlive() && (System.currentTimeMillis() - startTime < REGEX_TIMOUT_MS))
         {
            Thread.sleep(50);
         }

         if (thread.isAlive())
         {
            thread.interrupt();
            Thread.sleep(1000);
            result.setMatches(false);
            result.setReplaced("");
            result.setText("");
            result.setError("Regex processing timed out.");
         }
      }
      catch (InterruptedException e) {
         throw new RuntimeException(e);
      }

      return result;
   }

   // TODO add this as an option?
   public String javaMode(String regex)
   {
      StringBuilder result = new StringBuilder();

      int count = 0;
      for (int i = 0; i < regex.length(); i++) {
         char c = regex.charAt(i);

         if (c == '\\')
         {
            count++;
         }

         if (count % 2 == 1)
         {
            if (c != '\\')
            {
               if (i + 1 <= regex.length())
               {
                  throw new RegexException("Unterminated escape sequence at character " + i + ": " + result);
               }
            }
            else if (i + 1 == regex.length())
            {
               throw new RegexException("Unterminated escape sequence at character " + i + ": " + result);
            }
         }
         result.append(c);
      }
      return result.toString().replaceAll("\\\\\\\\", "\\\\");
   }

}