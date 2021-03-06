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
package org.apache.shindig.gadgets.rewrite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.apache.shindig.common.uri.Uri;
import org.apache.shindig.common.uri.UriBuilder;
import org.apache.shindig.gadgets.rewrite.DomWalker.Visitor.VisitStatus;
import org.apache.shindig.gadgets.uri.ConcatUriManager;
import org.junit.Before;
import org.junit.Test;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Map;

public class ConcatVisitorTest extends DomWalkerTestBase {
  private static final String JS1_URL_STR = "http://one.com/foo.js";
  private Node js1;
  
  private static final String JS2_URL_STR = "http://two.com/foo.js";
  private Node js2;
  
  private static final String JS3_URL_STR = "http://three.com/foo.js";
  private Node js3;
  
  private static final String JS4_URL_STR = "http://four.com/foo.js";
  private Node js4;
  
  private static final String JS5_URL_STR = "http://~^|BAD |^/foo.js";
  private Node js5;
  
  private static final String JS6_URL_STR = "http://six.com/foo.js";
  private Node js6;
  
  private static final String CSS1_URL_STR = "http://one.com/foo.js";
  private Node css1;
  
  private static final String CSS2_URL_STR = "http://two.com/foo.js";
  private Node css2;
  
  private static final String CSS3_URL_STR = "http://three.com/foo.js";
  private Node css3;
  
  private static final String CSS4_URL_STR = "http://four.com/foo.js";
  private Node css4;
  
  private static final Uri CONCAT_BASE_URI = Uri.parse("http://test.com/proxy");
  
  @Before
  public void setUp() {
    super.setUp();
    js1 = elem("script", "src", JS1_URL_STR);
    js2 = elem("script", "src", JS2_URL_STR);
    js3 = elem("script", "src", JS3_URL_STR);
    js4 = elem("script", "src", JS4_URL_STR);
    js5 = elem("script", "src", JS5_URL_STR);
    js6 = elem("script", "src", JS6_URL_STR);
    css1 = elem("link", "rel", "stylesheet", "type", "text/css", "href", CSS1_URL_STR);
    css2 = elem("link", "rel", "stylesheet", "type", "text/css", "href", CSS2_URL_STR);
    css3 = elem("link", "rel", "stylesheet", "type", "text/css", "href", CSS3_URL_STR);
    css4 = elem("link", "rel", "stylesheet", "type", "text/css", "href", CSS4_URL_STR);
  }
  
  @Test
  public void dontVisitSingleJs() throws Exception {
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(js1, null, false));
  }
  
  @Test
  public void dontVisitSingleCss() throws Exception {
    assertEquals(VisitStatus.BYPASS, getVisitStatusCss(css1, null));
  }
  
  @Test
  public void dontVisitJsWithoutSrc() throws Exception {
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(elem("script"), null, false));
  }
  
  @Test
  public void dontVisitUnknown() throws Exception {
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(elem("div"), null, true));
    assertEquals(VisitStatus.BYPASS, getVisitStatusCss(elem("div"), null));
  }
  
  @Test
  public void dontVisitContigJsMiddleNotRewritable() throws Exception {
    ContentRewriterFeature.Config config = config(".*two.*", false);
    seqNodes(js1, js2, js3);
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(config, js1));
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(config, js2));
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(config, js3));
  }
  
  @Test
  public void dontVisitContigCssMiddleNotRewritable() throws Exception {
    ContentRewriterFeature.Config config = config(".*two.*", true);
    seqNodes(css1, css2, css3);
    assertEquals(VisitStatus.BYPASS, getVisitStatusCss(config, css1));
    assertEquals(VisitStatus.BYPASS, getVisitStatusCss(config, css2));
    assertEquals(VisitStatus.BYPASS, getVisitStatusCss(config, css3));
  }
  
  @Test
  public void dontVisitSeparatedJsNotSplit() throws Exception {
    ContentRewriterFeature.Config config = config(null, false);
    Node sep1 = elem("div");
    Node sep2 = elem("span");
    seqNodes(js1, sep1, js2, sep2, js3);
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(config, js1));
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(config, sep1));
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(config, js2));
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(config, sep2));
    assertEquals(VisitStatus.BYPASS, getVisitStatusJs(config, js3));
  }
  
  @Test
  public void visitRelFreeCss() throws Exception {
    Node node = elem("link", "type", "text/css", "href", CSS1_URL_STR);
    seqNodes(node, css1);
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusCss(node, null));
  }
  
  @Test
  public void visitTypeCssFreeCss() throws Exception {
    Node node = elem("link", "rel", "stylesheet", "href", CSS1_URL_STR);
    seqNodes(node, css1);
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusCss(node, null));
  }
  
  @Test
  public void dontVisitCssWithoutAttribs() throws Exception {
    Node node = elem("link", "href", CSS1_URL_STR);
    seqNodes(node, css1);
    assertEquals(VisitStatus.BYPASS, getVisitStatusCss(node, null));
  }
  
  @Test
  public void visitContigJs() throws Exception {
    seqNodes(js1, js2, js3);
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js1, null, false));
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js2, null, false));
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js3, null, false));
  }
  
  @Test
  public void visitContigCss() throws Exception {
    seqNodes(css1, css2, css3);
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusCss(css1, null));
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusCss(css2, null));
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusCss(css3, null));
  }
  
  @Test
  public void visitSplitJsSingle() throws Exception {
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js1, null, true));
  }
  
  @Test
  public void visitSplitJsSeparated() throws Exception {
    seqNodes(js1, elem("span"), js2, elem("div"), js3);
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js1, null, true));
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js2, null, true));
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js3, null, true));
  }
  
  @Test
  public void visitSplitJsContiguous() throws Exception {
    seqNodes(js1, js2, js3);
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js1, null, true));
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js2, null, true));
    assertEquals(VisitStatus.RESERVE_NODE, getVisitStatusJs(js3, null, true));
  }
  
  @Test
  public void concatSingleBatchJs() throws Exception {
    List<Node> nodes = seqNodes(js1, js2, js3);
    Node parent = js1.getParentNode();
    
    // Sanity check.
    assertEquals(3, parent.getChildNodes().getLength());
    
    SimpleConcatUriManager mgr = simpleMgr();
    ConcatVisitor.Js rewriter = new ConcatVisitor.Js(config(null, false), mgr);
    assertTrue(rewriter.revisit(gadget(), nodes));
    
    // Should be left with a single JS node child to parent.
    assertEquals(1, parent.getChildNodes().getLength());
    Element concatNode = (Element)parent.getChildNodes().item(0);
    Uri concatUri = Uri.parse(StringUtils.replace(concatNode.getAttribute("src"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri.getPath());
    assertEquals(JS1_URL_STR, concatUri.getQueryParameter("1"));
    assertEquals(JS2_URL_STR, concatUri.getQueryParameter("2"));
    assertEquals(JS3_URL_STR, concatUri.getQueryParameter("3"));
  }
  
  @Test
  public void concatSingleBatchCss() throws Exception {
    List<Node> nodes = seqNodes(css1, css2, css3);
    Node parent = css1.getParentNode();
    
    // Sanity check.
    assertEquals(3, parent.getChildNodes().getLength());
    
    SimpleConcatUriManager mgr = simpleMgr();
    ConcatVisitor.Css rewriter = new ConcatVisitor.Css(config(null, false), mgr);
    assertTrue(rewriter.revisit(gadget(), nodes));
    
    // Should be left with a single JS node child to parent.
    assertEquals(1, parent.getChildNodes().getLength());
    Element concatNode = (Element)parent.getChildNodes().item(0);
    Uri concatUri = Uri.parse(StringUtils.replace(concatNode.getAttribute("href"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri.getPath());
    assertEquals(CSS1_URL_STR, concatUri.getQueryParameter("1"));
    assertEquals(CSS2_URL_STR, concatUri.getQueryParameter("2"));
    assertEquals(CSS3_URL_STR, concatUri.getQueryParameter("3"));
  }
  
  @Test
  public void concatMultiBatchJs() throws Exception {
    List<Node> fullListJs = Lists.newArrayList();
    fullListJs.addAll(seqNodes(js1, js2));
    Node parent1 = js1.getParentNode();
    assertEquals(2, parent1.getChildNodes().getLength());
    
    fullListJs.addAll(seqNodes(js3, js4));
    Node parent2 = js3.getParentNode();
    assertEquals(2, js3.getParentNode().getChildNodes().getLength());
    
    SimpleConcatUriManager mgr = simpleMgr();
    ConcatVisitor.Js rewriter = new ConcatVisitor.Js(config(null, false), mgr);
    assertTrue(rewriter.revisit(gadget(), fullListJs));
    
    // Should have been independently concatenated.
    assertEquals(1, parent1.getChildNodes().getLength());
    Element cn1 = (Element)parent1.getChildNodes().item(0);
    Uri concatUri1 = Uri.parse(StringUtils.replace(cn1.getAttribute("src"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri1.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri1.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri1.getPath());
    assertEquals(JS1_URL_STR, concatUri1.getQueryParameter("1"));
    assertEquals(JS2_URL_STR, concatUri1.getQueryParameter("2"));
    assertNull(concatUri1.getQueryParameter("3"));
    
    assertEquals(1, parent2.getChildNodes().getLength());
    Element cn2 = (Element)parent2.getChildNodes().item(0);
    Uri concatUri2 = Uri.parse(StringUtils.replace(cn2.getAttribute("src"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri2.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri2.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri2.getPath());
    assertEquals(JS3_URL_STR, concatUri2.getQueryParameter("1"));
    assertEquals(JS4_URL_STR, concatUri2.getQueryParameter("2"));
    assertNull(concatUri2.getQueryParameter("3"));
  }
  
  @Test
  public void concatMultiBatchCss() throws Exception {
    List<Node> fullListCss = Lists.newArrayList();
    fullListCss.addAll(seqNodes(css1, css2));
    Node parent1 = css1.getParentNode();
    assertEquals(2, parent1.getChildNodes().getLength());
    
    fullListCss.addAll(seqNodes(css3, css4));
    Node parent2 = css3.getParentNode();
    assertEquals(2, css3.getParentNode().getChildNodes().getLength());
    
    SimpleConcatUriManager mgr = simpleMgr();
    ConcatVisitor.Css rewriter = new ConcatVisitor.Css(config(null, false), mgr);
    assertTrue(rewriter.revisit(gadget(), fullListCss));
    
    // Should have been independently concatenated.
    assertEquals(1, parent1.getChildNodes().getLength());
    Element cn1 = (Element)parent1.getChildNodes().item(0);
    Uri concatUri1 = Uri.parse(StringUtils.replace(cn1.getAttribute("href"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri1.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri1.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri1.getPath());
    assertEquals(CSS1_URL_STR, concatUri1.getQueryParameter("1"));
    assertEquals(CSS2_URL_STR, concatUri1.getQueryParameter("2"));
    assertNull(concatUri1.getQueryParameter("3"));
    
    assertEquals(1, parent2.getChildNodes().getLength());
    Element cn2 = (Element)parent2.getChildNodes().item(0);
    Uri concatUri2 = Uri.parse(StringUtils.replace(cn2.getAttribute("href"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri2.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri2.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri2.getPath());
    assertEquals(CSS3_URL_STR, concatUri2.getQueryParameter("1"));
    assertEquals(CSS4_URL_STR, concatUri2.getQueryParameter("2"));
    assertNull(concatUri2.getQueryParameter("3"));
  }
  
  @Test
  public void concatMultiBatchJsBadBatch() throws Exception {
    List<Node> fullListJs = Lists.newArrayList();
    fullListJs.addAll(seqNodes(js1, js2));
    Node parent1 = js1.getParentNode();
    assertEquals(2, parent1.getChildNodes().getLength());
    
    fullListJs.addAll(seqNodes(js5, js6));
    Node parent3 = js5.getParentNode();
    assertEquals(2, parent3.getChildNodes().getLength());
    
    fullListJs.addAll(seqNodes(js3, js4));
    Node parent2 = js3.getParentNode();
    assertEquals(2, js3.getParentNode().getChildNodes().getLength());
    
    SimpleConcatUriManager mgr = simpleMgr();
    ConcatVisitor.Js rewriter = new ConcatVisitor.Js(config(null, false), mgr);
    assertTrue(rewriter.revisit(gadget(), fullListJs));
    
    // Should have been independently concatenated. Batches #1 and #2 are OK. Middle skipped.
    assertEquals(1, parent1.getChildNodes().getLength());
    Element cn1 = (Element)parent1.getChildNodes().item(0);
    Uri concatUri1 = Uri.parse(StringUtils.replace(cn1.getAttribute("src"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri1.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri1.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri1.getPath());
    assertEquals(JS1_URL_STR, concatUri1.getQueryParameter("1"));
    assertEquals(JS2_URL_STR, concatUri1.getQueryParameter("2"));
    
    assertEquals(2, parent3.getChildNodes().getLength());
    assertSame(js5, parent3.getChildNodes().item(0));
    assertSame(js6, parent3.getChildNodes().item(1));
    
    assertEquals(1, parent2.getChildNodes().getLength());
    Element cn2 = (Element)parent2.getChildNodes().item(0);
    Uri concatUri2 = Uri.parse(StringUtils.replace(cn2.getAttribute("src"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri2.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri2.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri2.getPath());
    assertEquals(JS3_URL_STR, concatUri2.getQueryParameter("1"));
    assertEquals(JS4_URL_STR, concatUri2.getQueryParameter("2"));
  }
  
  @Test
  public void concatSplitJsSingleBatch() throws Exception {
    List<Node> nodes = seqNodes(js1, js2);
    Node parent = js1.getParentNode();
    assertEquals(2, parent.getChildNodes().getLength());
    
    SimpleConcatUriManager mgr = simpleMgr();
    ConcatVisitor.Js rewriter = new ConcatVisitor.Js(config(null, true), mgr);
    assertTrue(rewriter.revisit(gadget(), nodes));
    
    // Same number of nodes. Now the second JS node is a new script node eval'ing JS.
    // For test purposes the code is just the Uri.
    assertEquals(3, parent.getChildNodes().getLength());
    Element jsConcat = (Element)parent.getChildNodes().item(0);
    assertEquals("script", jsConcat.getTagName());
    Uri concatUri = Uri.parse(StringUtils.replace(jsConcat.getAttribute("src"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri.getPath());
    assertEquals(JS1_URL_STR, concatUri.getQueryParameter("1"));
    assertEquals(JS2_URL_STR, concatUri.getQueryParameter("2"));
    assertNull(concatUri.getQueryParameter("3"));
    assertEquals("1", concatUri.getQueryParameter("SPLIT"));
    
    // Split-eval nodes 1 and 2
    Element splitEval1 = (Element)parent.getChildNodes().item(1);
    assertEquals("script", splitEval1.getTagName());
    assertNull(splitEval1.getAttributeNode("src"));
    assertEquals(JS1_URL_STR, splitEval1.getTextContent());
    
    Element splitEval2 = (Element)parent.getChildNodes().item(2);
    assertEquals("script", splitEval2.getTagName());
    assertNull(splitEval2.getAttributeNode("src"));
    assertEquals(JS2_URL_STR, splitEval2.getTextContent());
  }
  
  @Test
  public void concatSplitJsSplitNodes() throws Exception {
    Node parent = doc.createElement("container");
    parent.appendChild(doc.createElement("div"));
    parent.appendChild(js1);
    parent.appendChild(doc.createTextNode("text"));
    parent.appendChild(doc.createComment("comment"));
    parent.appendChild(js2);
    parent.appendChild(doc.createElement("span"));
    List<Node> nodes = ImmutableList.of(js1, js2);
    assertEquals(6, parent.getChildNodes().getLength());
    
    SimpleConcatUriManager mgr = simpleMgr();
    ConcatVisitor.Js rewriter = new ConcatVisitor.Js(config(null, true), mgr);
    assertTrue(rewriter.revisit(gadget(), nodes));
    
    // Same number of nodes. Now the second JS node is a new script node eval'ing JS.
    // For test purposes the code is just the Uri.
    assertEquals(7, parent.getChildNodes().getLength());
    Element jsConcat = (Element)parent.getChildNodes().item(1);
    assertEquals("script", jsConcat.getTagName());
    Uri concatUri = Uri.parse(StringUtils.replace(jsConcat.getAttribute("src"), "&amp;", "&"));
    assertEquals(CONCAT_BASE_URI.getScheme(), concatUri.getScheme());
    assertEquals(CONCAT_BASE_URI.getAuthority(), concatUri.getAuthority());
    assertEquals(CONCAT_BASE_URI.getPath(), concatUri.getPath());
    assertEquals(JS1_URL_STR, concatUri.getQueryParameter("1"));
    assertEquals(JS2_URL_STR, concatUri.getQueryParameter("2"));
    assertNull(concatUri.getQueryParameter("3"));
    assertEquals("1", concatUri.getQueryParameter("SPLIT"));
    
    // Split-eval nodes 1 and 2
    Element splitEval1 = (Element)parent.getChildNodes().item(2);
    assertEquals("script", splitEval1.getTagName());
    assertNull(splitEval1.getAttributeNode("src"));
    assertEquals(JS1_URL_STR, splitEval1.getTextContent());
    
    Element splitEval2 = (Element)parent.getChildNodes().item(5);
    assertEquals("script", splitEval2.getTagName());
    assertNull(splitEval2.getAttributeNode("src"));
    assertEquals(JS2_URL_STR, splitEval2.getTextContent());
  }
  
  private VisitStatus getVisitStatusJs(ContentRewriterFeature.Config config, Node node)
      throws RewritingException {
    return new ConcatVisitor.Js(config, null).visit(gadget(), node);
  }
  
  private VisitStatus getVisitStatusJs(Node node, String rewriteRegex, boolean splitJs)
      throws Exception {
    ContentRewriterFeature.Config config = config(rewriteRegex, splitJs);
    return getVisitStatusJs(config, node);
  }
  
  private VisitStatus getVisitStatusCss(ContentRewriterFeature.Config config, Node node)
      throws RewritingException {
    return new ConcatVisitor.Css(config, null).visit(gadget(), node);
  }
  
  private VisitStatus getVisitStatusCss(Node node, String rewriteRegex)
      throws Exception {
    // True, but never used (splitJS support)
    ContentRewriterFeature.Config config = config(rewriteRegex, true);
    return getVisitStatusCss(config, node);
  }
  
  private ContentRewriterFeature.Config config(String exclude, boolean splitJs) {
    return new ContentRewriterFeature.DefaultConfig(".*", exclude == null ? "" : exclude,
        "0", "", "false", splitJs ? "true" : "false");
  }
  
  private List<Node> seqNodes(Node... nodes) {
    Node container = doc.createElement("container");
    List<Node> seq = Lists.newArrayListWithCapacity(nodes.length);
    for (Node node : nodes) {
      container.appendChild(node);
      seq.add(node);
    }
    return seq;
  }
  
  private SimpleConcatUriManager simpleMgr() {
    return new SimpleConcatUriManager(CONCAT_BASE_URI);
  }
  
  private static class SimpleConcatUriManager implements ConcatUriManager {
    private final Uri base;
    
    private SimpleConcatUriManager(Uri base) {
      this.base = base;
    }

    public List<ConcatData> make(List<ConcatUri> batches, boolean isAdjacent) {
      List<ConcatData> results = Lists.newArrayListWithCapacity(batches.size());
      for (ConcatUri batch : batches) {
        UriBuilder uriBuilder = new UriBuilder(base);
        Integer i = 1;
        for (Uri uri : batch.getBatch()) {
          uriBuilder.addQueryParameter((i++).toString(), uri.toString());
        }
        Map<Uri, String> snippets = Maps.newHashMap();
        if (!isAdjacent) {
          for (Uri uri : batch.getBatch()) {
            snippets.put(uri, uri.toString());
          }
          uriBuilder.addQueryParameter("SPLIT", "1");
        }
        results.add(new ConcatData(uriBuilder.toUri(), snippets));
      }
      return results;
    }

    public ConcatUri process(Uri uri) {
      // Not used in test code.
      throw new UnsupportedOperationException();
    }
    
  }
}
