/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shindig.gadgets.servlet;

import com.google.caja.lexer.ExternalReference;
import com.google.caja.lexer.InputSource;
import com.google.caja.lexer.escaping.Escaping;
import com.google.caja.opensocial.DefaultGadgetRewriter;
import com.google.caja.opensocial.GadgetRewriteException;
import com.google.caja.opensocial.UriCallback;
import com.google.caja.opensocial.UriCallbackException;
import com.google.caja.parser.html.Nodes;
import com.google.caja.render.Concatenator;
import com.google.caja.reporting.BuildInfo;
import com.google.caja.reporting.Message;
import com.google.caja.reporting.MessageContext;
import com.google.caja.reporting.MessageLevel;
import com.google.caja.reporting.MessageQueue;
import com.google.caja.reporting.RenderContext;
import com.google.caja.reporting.SimpleMessageQueue;
import com.google.caja.reporting.SnippetProducer;
import com.google.caja.reporting.HtmlSnippetProducer;
import com.google.caja.util.Pair;
import com.google.common.collect.Maps;
import com.google.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.apache.shindig.common.cache.Cache;
import org.apache.shindig.common.cache.CacheProvider;
import org.apache.shindig.common.util.HashUtil;
import org.apache.shindig.gadgets.Gadget;
import org.apache.shindig.gadgets.parse.HtmlSerialization;
import org.apache.shindig.gadgets.parse.HtmlSerializer;
import org.apache.shindig.gadgets.rewrite.MutableContent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.Map;
import java.util.logging.Logger;

public class CajaContentRewriter implements org.apache.shindig.gadgets.rewrite.GadgetRewriter {
  public static final String CAJOLED_DOCUMENTS = "cajoledDocuments";

  private final Logger logger = Logger.getLogger(CajaContentRewriter.class.getName());
  private Cache<String, Element> cajoledCache;

  @Inject
  public void setCacheProvider(CacheProvider cacheProvider) {
    cajoledCache = cacheProvider.createCache(CAJOLED_DOCUMENTS);
    logger.info("Cajoled cache created" + cajoledCache);
  }

  public void rewrite(Gadget gadget, MutableContent content) {
    if (gadget.getSpec().getModulePrefs().getFeatures().containsKey("caja") ||
        "1".equals(gadget.getContext().getParameter("caja"))) {

      final URI retrievedUri = gadget.getContext().getUrl().toJavaUri();
      UriCallback cb = new UriCallback() {
        public Reader retrieve(ExternalReference externalReference, String string)
            throws UriCallbackException {
          logger.info("Retrieving " + externalReference.toString());
          Reader in = null;
          try {
            URI resourceUri = retrievedUri.resolve(externalReference.getUri());
            in = new InputStreamReader(
                resourceUri.toURL().openConnection().getInputStream(), "UTF-8");
            char[] buf = new char[4096];
            StringBuilder sb = new StringBuilder();
            for (int n; (n = in.read(buf)) > 0;) {
              sb.append(buf, 0, n);
            }
            return new StringReader(sb.toString());
          } catch (java.net.MalformedURLException ex) {
            throw new UriCallbackException(externalReference, ex);
          } catch (IOException ex) {
            throw new UriCallbackException(externalReference, ex);
          } finally {
            try {
              in.close();
            } catch (IOException e) {
              // Not sure what else we can do here
              throw new RuntimeException(e);
            }
          }
        }

        public URI rewrite(ExternalReference externalReference, String mimeType) {
          URI uri = externalReference.getUri();
          if (uri.getScheme().equalsIgnoreCase("https") ||
              uri.getScheme().equalsIgnoreCase("http")) {
            return retrievedUri.resolve(uri);
          } else if ("javascript".equalsIgnoreCase(uri.getScheme())) {
              // Commonly used javascript url for links with onclick handlers
              return uri.toString().equals("javascript:void(0)") ? uri : null;
          } else {
            return null;
          }
        }
      };
      String key = HashUtil.rawChecksum(content.getContent().getBytes());
      Document doc = content.getDocument();
      Node root = doc.createDocumentFragment();
      root.appendChild(doc.getDocumentElement());
      Element cajoledOutput = null;
      if (null != cajoledCache) {
        cajoledOutput = cajoledCache.getElement(key);
        if (null != cajoledOutput) {
          createContainerFor(doc, doc.adoptNode(cajoledOutput));
          content.documentChanged();
          HtmlSerialization.attach(doc, new CajaHtmlSerializer(), null);
          return;
        }
      }
      MessageQueue mq = new SimpleMessageQueue();
      BuildInfo bi = BuildInfo.getInstance();
      DefaultGadgetRewriter rw = new DefaultGadgetRewriter(bi, mq);
      InputSource is = new InputSource(retrievedUri);
      boolean safe = false;
      
      try {
        Pair<Node, Element> htmlAndJs = rw.rewriteContent(retrievedUri, root, cb);
        Node html = htmlAndJs.a;
        Element script = htmlAndJs.b;
        
        cajoledOutput = doc.createElement("div");
        cajoledOutput.setAttribute("id", "cajoled-output");
        cajoledOutput.setAttribute("classes", "g___");
        cajoledOutput.setAttribute("style", "position: relative;");
        
        cajoledOutput.appendChild(doc.adoptNode(html));
        cajoledOutput.appendChild(tameCajaClientApi(doc));
        cajoledOutput.appendChild(doc.adoptNode(script));
        
        Element messagesNode = formatErrors(doc, is, content.getContent(), mq, 
          /* visible */ false);
        cajoledOutput.appendChild(messagesNode);
        if (cajoledCache != null) {
          cajoledCache.addElement(key, cajoledOutput);
        }
        createContainerFor(doc, cajoledOutput);
        content.documentChanged();
        safe = true;
        HtmlSerialization.attach(doc, new CajaHtmlSerializer(), null);
      } catch (GadgetRewriteException e) {
        // There were cajoling errors
        // Content is only used to produce useful snippets with error messages
        createContainerFor(doc, 
          formatErrors(doc, is, content.getContent(), mq, true /* visible */));
        logException(e, mq);
        safe = true;
      } finally {
        if (!safe) {
          // Fail safe
          content.setContent("");
        }
      }
    }
  }

  private void createContainerFor(Document doc, Node el) {
    Element docEl = doc.createElement("html");
    Element head = doc.createElement("head");
    Element body = doc.createElement("body");
    doc.appendChild(docEl);
    docEl.appendChild(head);
    docEl.appendChild(body);
    body.appendChild(el);
  }

  private Element formatErrors(Document doc, InputSource is,
      CharSequence orig, MessageQueue mq, boolean visible) {
    MessageContext mc = new MessageContext();
    Map<InputSource, CharSequence> originalSrc = Maps.newHashMap();
    originalSrc.put(is, orig);
    mc.addInputSource(is);
    SnippetProducer sp = new SnippetProducer(originalSrc, mc);

    Element errElement = doc.createElement("ul");
    // Style defined in gadgets.css
    errElement.setAttribute("class", "gadgets-messages");
    if (!visible) {
      errElement.setAttribute("style", "display: none");
    }
    for (Message msg : mq.getMessages()) {
      // Ignore LINT messages
      if (MessageLevel.LINT.compareTo(msg.getMessageLevel()) <= 0) {
        String snippet = sp.getSnippet(msg);
        String messageText = msg.getMessageLevel().name() + ' ' + 
          html(msg.format(mc)) + ':' + snippet;
        Element li = doc.createElement("li");
        li.appendChild(doc.createTextNode(messageText.toString()));
        errElement.appendChild(li);
      }
    }
    return errElement;
  }

  private static String html(CharSequence s) {
    StringBuilder sb = new StringBuilder();
    Escaping.escapeXml(s, false, sb);
    return sb.toString();
  }

  private Element tameCajaClientApi(Document doc) {
    Element scriptElement = doc.createElement("script");
    scriptElement.setAttribute("type", "text/javascript");
    scriptElement.appendChild(doc.createTextNode("caja___.enable()"));
    return scriptElement;
  }

  private void logException(Exception cause, MessageQueue mq) {
    StringBuilder errbuilder = new StringBuilder();
    MessageContext mc = new MessageContext();
    if (cause != null) {
      errbuilder.append(cause).append('\n');
    }
    for (Message m : mq.getMessages()) {
      errbuilder.append(m.format(mc)).append('\n');
    }
    logger.info("Unable to cajole gadget: " + errbuilder);
  }

  private static class CajaHtmlSerializer implements HtmlSerializer {
    public String serialize(Document doc) {
      StringWriter sw = HtmlSerialization.createWriter(doc);
      return Nodes.render(doc, new RenderContext(new Concatenator(sw, null)).asXml());
    }
  }
}
