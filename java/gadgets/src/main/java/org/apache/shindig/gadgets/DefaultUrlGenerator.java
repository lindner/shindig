/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shindig.gadgets;

import org.apache.shindig.common.util.HashUtil;
import org.apache.shindig.common.util.Utf8UrlCoder;
import org.apache.shindig.gadgets.spec.GadgetSpec;
import org.apache.shindig.gadgets.spec.UserPref;
import org.apache.shindig.gadgets.spec.View;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Default url generator. Produces js urls that include checksums for cache-busting.
 *
 * TODO: iframe and js url generation are two distinct things, and should probably be different
 * interfaces.
 */
@Singleton
public class DefaultUrlGenerator implements UrlGenerator {
  private final static Pattern ALLOWED_FEATURE_NAME = Pattern.compile("[0-9a-zA-Z\\.\\-]+");
  private final String jsPrefix;
  private final String iframePrefix;
  private final String jsChecksum;

  @Inject
  public DefaultUrlGenerator(@Named("shindig.urls.iframe.prefix") String iframePrefix,
                             @Named("shindig.urls.js.prefix") String jsPrefix,
                             GadgetFeatureRegistry registry) {
    this.iframePrefix = iframePrefix;
    this.jsPrefix = jsPrefix;

    StringBuilder jsBuf = new StringBuilder();
    for (GadgetFeature feature : registry.getAllFeatures()) {
      for (JsLibrary library : feature.getJsLibraries(null, null)) {
        jsBuf.append(library.getContent());
      }
    }
    jsChecksum = HashUtil.checksum(jsBuf.toString().getBytes());
  }

  public String getBundledJsUrl(Collection<String> features, GadgetContext context) {
    return jsPrefix + getBundledJsParam(features, context);
  }

  public String getBundledJsParam(Collection<String> features, GadgetContext context) {
    StringBuilder buf = new StringBuilder();
    boolean first = false;
    for (String feature : features) {
      if (ALLOWED_FEATURE_NAME.matcher(feature).matches()) {
        if (!first) {
          first = true;
        } else {
          buf.append(':');
        }
        buf.append(feature);
      }
    }
    if (!first) {
      buf.append("core");
    }
    buf.append(".js?v=").append(jsChecksum)
       .append("&container=").append(context.getContainer())
       .append("&debug=").append(context.getDebug() ? "1" : "0");
    return buf.toString();
  }

  /**
   * TODO: This is in need of a rewrite most likely. It doesn't even take locked domain into
   * consideration!
   */
  public String getIframeUrl(Gadget gadget) {
    StringBuilder buf = new StringBuilder();
    GadgetContext context = gadget.getContext();
    GadgetSpec spec = gadget.getSpec();
    String url = context.getUrl().toString();
    View view = gadget.getCurrentView();
    View.ContentType type;
    if (view == null) {
      type = View.ContentType.HTML;
    } else {
      type = view.getType();
    }
    switch (type) {
      case URL:
        // type = url
        String href = view.getHref().toString();
        buf.append(href);
        if (href.indexOf('?') == -1) {
          buf.append('?');
        } else {
          buf.append('&');
        }
        break;
      case HTML:
      default:
        buf.append(iframePrefix);
        break;
    }
    buf.append("container=").append(context.getContainer());
    if (context.getModuleId() != 0) {
      buf.append("&mid=").append(context.getModuleId());
    }
    if (context.getIgnoreCache()) {
      buf.append("&nocache=1");
    } else {
      buf.append("&v=").append(spec.getChecksum());
    }

    buf.append("&lang=").append(context.getLocale().getLanguage());
    buf.append("&country=").append(context.getLocale().getCountry());
    buf.append("&view=").append(context.getView());

    UserPrefs prefs = context.getUserPrefs();
    for (UserPref pref : gadget.getSpec().getUserPrefs()) {
      String name = pref.getName();
      String value = prefs.getPref(name);
      if (value == null) {
        value = pref.getDefaultValue();
      }
      buf.append("&up_").append(Utf8UrlCoder.encode(pref.getName()))
         .append('=').append(Utf8UrlCoder.encode(value));
    }
    // add url last to work around browser bugs
    if(!type.equals(View.ContentType.URL)) {
        buf.append("&url=")
           .append(Utf8UrlCoder.encode(url));
      }

    return buf.toString();
  }
}