package ru.noties.markwon.renderer;

import android.support.annotation.NonNull;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StrikethroughSpan;
import android.text.style.URLSpan;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.ListBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

import ru.noties.debug.Debug;
import ru.noties.markwon.spans.AsyncDrawable;
import ru.noties.markwon.spans.AsyncDrawableSpan;
import ru.noties.markwon.spans.BlockQuoteSpan;
import ru.noties.markwon.spans.BulletListItemSpan;
import ru.noties.markwon.spans.CodeSpan;
import ru.noties.markwon.spans.EmphasisSpan;
import ru.noties.markwon.spans.HeadingSpan;
import ru.noties.markwon.spans.LinkSpan;
import ru.noties.markwon.spans.OrderedListItemSpan;
import ru.noties.markwon.spans.StrongEmphasisSpan;
import ru.noties.markwon.spans.ThematicBreakSpan;

public class SpannableMarkdownVisitor extends AbstractVisitor {

    private final SpannableConfiguration configuration;
    private final SpannableStringBuilder builder;

    private int blockQuoteIndent;
    private int listLevel;

    public SpannableMarkdownVisitor(
            @NonNull SpannableConfiguration configuration,
            @NonNull SpannableStringBuilder builder
    ) {
        this.configuration = configuration;
        this.builder = builder;
    }

    @Override
    public void visit(Text text) {
//        Debug.i(text);
        builder.append(text.getLiteral());
    }

    @Override
    public void visit(StrongEmphasis strongEmphasis) {
//        Debug.i(strongEmphasis);
        final int length = builder.length();
        visitChildren(strongEmphasis);
        setSpan(length, new StrongEmphasisSpan());
    }

    @Override
    public void visit(Emphasis emphasis) {
//        Debug.i(emphasis);
        final int length = builder.length();
        visitChildren(emphasis);
        setSpan(length, new EmphasisSpan());
    }

    @Override
    public void visit(BlockQuote blockQuote) {

//        Debug.i(blockQuote);

        newLine();
        if (blockQuoteIndent != 0) {
            builder.append('\n');
        }

        final int length = builder.length();

        blockQuoteIndent += 1;

        visitChildren(blockQuote);

        setSpan(length, new BlockQuoteSpan(
                configuration.theme(),
                blockQuoteIndent
        ));

        blockQuoteIndent -= 1;

        newLine();
        if (blockQuoteIndent == 0) {
            builder.append('\n');
        }
    }

    @Override
    public void visit(Code code) {

//        Debug.i(code);

        final int length = builder.length();

        // NB, in order to provide a _padding_ feeling code is wrapped inside two unbreakable spaces
        // unfortunately we cannot use this for multiline code as we cannot control there a new line break will be inserted
        builder.append('\u00a0');
        builder.append(code.getLiteral());
        builder.append('\u00a0');

        setSpan(length, new CodeSpan(
                configuration.theme(),
                false
        ));
    }

    @Override
    public void visit(FencedCodeBlock fencedCodeBlock) {

        newLine();

        final int length = builder.length();

        // empty lines on top & bottom
        builder.append('\u00a0').append('\n');
        builder.append(
                configuration.syntaxHighlight()
                        .highlight(fencedCodeBlock.getInfo(), fencedCodeBlock.getLiteral())
        );
        builder.append('\u00a0').append('\n');

        setSpan(length, new CodeSpan(
                configuration.theme(),
                true
        ));

        newLine();
        builder.append('\n');
    }

    @Override
    public void visit(BulletList bulletList) {
        visitList(bulletList);
    }

    @Override
    public void visit(OrderedList orderedList) {
        visitList(orderedList);
    }

    private void visitList(Node node) {
//        Debug.i(node);
        newLine();
        visitChildren(node);
        newLine();
        if (listLevel == 0 && blockQuoteIndent == 0) {
            builder.append('\n');
        }
    }

    @Override
    public void visit(ListItem listItem) {

//        Debug.i(listItem);

        final int length = builder.length();

        blockQuoteIndent += 1;
        listLevel += 1;

        // todo, can be a bullet list & ordered list (with leading numbers... looks like we need to `draw` numbers...

        final Node parent = listItem.getParent();
        if (parent instanceof OrderedList) {

            final int start = ((OrderedList) parent).getStartNumber();

            visitChildren(listItem);

            setSpan(length, new OrderedListItemSpan(
                    configuration.theme(),
                    String.valueOf(start) + "." + '\u00a0',
                    blockQuoteIndent,
                    length
            ));

            // after we have visited the children increment start number
            final OrderedList orderedList = (OrderedList) parent;
            orderedList.setStartNumber(orderedList.getStartNumber() + 1);

        } else {

            visitChildren(listItem);

            setSpan(length, new BulletListItemSpan(
                    configuration.theme(),
                    blockQuoteIndent,
                    listLevel - 1,
                    length
            ));
        }

        blockQuoteIndent -= 1;
        listLevel -= 1;

        newLine();
    }

    @Override
    public void visit(ThematicBreak thematicBreak) {

//        Debug.i(thematicBreak);

        newLine();

        final int length = builder.length();
        builder.append(' '); // without space it won't render
        setSpan(length, new ThematicBreakSpan(configuration.theme()));

        newLine();
        builder.append('\n');
    }

    @Override
    public void visit(Heading heading) {

//        Debug.i(heading);

        newLine();

        final int length = builder.length();
        visitChildren(heading);
        setSpan(length, new HeadingSpan(
                configuration.theme(),
                heading.getLevel(),
                builder.length())
        );

        newLine();

        // after heading we add another line anyway (no additional checks)
        builder.append('\n');
    }

    @Override
    public void visit(SoftLineBreak softLineBreak) {
        Debug.i(softLineBreak);
        newLine();
    }

    @Override
    public void visit(HardLineBreak hardLineBreak) {
        Debug.i(hardLineBreak);
        newLine();
    }

    @Override
    public void visit(CustomNode customNode) {

//        Debug.i(customNode);

        if (customNode instanceof Strikethrough) {
            final int length = builder.length();
            visitChildren(customNode);
            setSpan(length, new StrikethroughSpan());
        } else {
            super.visit(customNode);
        }
    }

    @Override
    public void visit(Paragraph paragraph) {

        final boolean inTightList = isInTightList(paragraph);

//        Debug.i(paragraph, inTightList, listLevel);

        if (!inTightList) {
            newLine();
        }

        visitChildren(paragraph);

        if (!inTightList) {
            newLine();

            if (blockQuoteIndent == 0) {
                builder.append('\n');
            }
        }
    }

    @Override
    public void visit(Image image) {

        final int length = builder.length();

        visitChildren(image);

        // if image has no link, create it (to open in external app)

        // we must check if anything _was_ added, as we need at least one char to render
        if (length == builder.length()) {
            builder.append(' '); // breakable space
        }

        final Node parent = image.getParent();
        final boolean link = parent != null && parent instanceof Link;

        setSpan(length, new AsyncDrawableSpan(
                configuration.theme(),
                new AsyncDrawable(
                        image.getDestination(),
                        configuration.asyncDrawableLoader()
                ),
                AsyncDrawableSpan.ALIGN_BOTTOM,
                link)
        );
    }

    @Override
    public void visit(HtmlBlock htmlBlock) {
        // http://spec.commonmark.org/0.18/#html-blocks
        Debug.i(htmlBlock, htmlBlock.getLiteral());
        super.visit(htmlBlock);
    }

    @Override
    public void visit(Link link) {
        final int length = builder.length();
        visitChildren(link);
        setSpan(length, new LinkSpan(configuration.theme(), link.getDestination(), configuration.linkResolver()));
    }

    private void setSpan(int start, @NonNull Object span) {
        builder.setSpan(span, start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void newLine() {
        if (builder.length() > 0
                && '\n' != builder.charAt(builder.length() - 1)) {
            builder.append('\n');
        }
    }

    private boolean isInTightList(Paragraph paragraph) {
        final Node parent = paragraph.getParent();
        if (parent != null) {
            final Node gramps = parent.getParent();
            if (gramps != null && gramps instanceof ListBlock) {
                ListBlock list = (ListBlock) gramps;
                return list.isTight();
            }
        }
        return false;
    }

//    private static String dump(Node node) {
//        final StringBuilder builder = new StringBuilder();
//        node.accept(new DumpVisitor(builder));
//        return builder.toString();
//    }
//
//    private static class DumpVisitor extends AbstractVisitor {
//        private final StringBuilder builder;
//
//        DumpVisitor(StringBuilder builder) {
//            this.builder = builder;
//        }
//
//        @Override
//        public void visit(Text text) {
//            builder.append(text.getLiteral());
//        }
//    }
}