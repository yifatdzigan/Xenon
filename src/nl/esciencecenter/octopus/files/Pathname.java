/*
 * Copyright 2013 Netherlands eScience Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.esciencecenter.octopus.files;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * Pathname contains a sequence of path elements separated by a separator.
 * 
 * @author Jason Maassen <J.Maassen@esciencecenter.nl>
 * @version 1.0
 * @since 1.0
 */
public class Pathname {

    /** The default separator to use. */
    public static final char DEFAULT_SEPARATOR = '/';

    /** The path elements in this pathname */
    private final String[] elements;

    /** The separator used in this pathname */
    private final char separator;

    class PathnameIterator implements Iterator<Pathname> {

        private int index = 1;

        @Override
        public boolean hasNext() {
            return index <= elements.length;
        }

        @Override
        public Pathname next() {

            if (index > elements.length) {
                throw new NoSuchElementException("No more elements available!");
            }

            return new Pathname(separator, Arrays.copyOf(elements, index++));
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove not supported!");
        }
    }

    /**
     * Create a new empty pathname using the default separator.
     */
    public Pathname() {
        this(DEFAULT_SEPARATOR, new String[0]);
    }

    /**
     * Create a new pathname using the path and the default separator.
     * 
     * If the <code>path</code> is <code>null</code> or an empty String, the resulting pathname is empty.
     * 
     * @param path
     *            the path to use.
     */
    public Pathname(String path) {
        this(DEFAULT_SEPARATOR, path);
    }

    /**
     * Create a new pathname using the given path elements and the default separator.
     * 
     * If the <code>elements</code> is <code>null</code> or an empty String array, the resulting pathname is empty.
     * 
     * @param elements
     *            the path elements to use.
     * 
     * @throws IllegalArgumentExeption If the elements arrays contains <code>null</code>, empty Strings, or Strings containing the
     *        seperator.
     */
    public Pathname(String... elements) {
        this(DEFAULT_SEPARATOR, elements);
    }

    /**
     * Create a new pathname by appending the provided <code>paths</code>.
     * 
     * If the <code>paths</code> is <code>null</code> the resulting pathname is empty.
     * 
     * @param paths
     *            the path elements to use.
     */
    public Pathname(Pathname... paths) {

        if (paths.length == 0) {
            elements = new String[0];
            separator = DEFAULT_SEPARATOR;
            return;
        }

        if (paths.length == 1) {
            elements = paths[0].elements;
            separator = paths[0].separator;
            return;
        }

        String[] tmp = merge(paths[0].elements, paths[1].elements);

        for (int i = 2; i < paths.length; i++) {
            tmp = merge(tmp, paths[i].elements);
        }

        elements = tmp;
        separator = paths[0].separator;
    }

    /**
     * Create a new pathname using the given path elements and the separator.
     * 
     * If the <code>elements</code> is <code>null</code> or an empty String array, the resulting pathname is empty.
     * 
     * Otherwise, each of the elements will be parsed individually, splitting them into elements wherever a separator is
     * encountered. Elements that are <code>null</code> or contain an empty string are ignored.
     * 
     * @param elements
     *            the path elements to use.
     * @param separator
     *            the separator to use.
     */
    public Pathname(char separator, String... elements) {

        this.separator = separator;

        if (elements == null || elements.length == 0) {
            this.elements = new String[0];
        } else {

            ArrayList<String> tmp = new ArrayList<String>();

            for (int i = 0; i < elements.length; i++) {

                String elt = elements[i];

                if (elt != null && elt.length() > 0) {
                    StringTokenizer tok = new StringTokenizer(elt, "" + this.separator);

                    while (tok.hasMoreTokens()) {
                        tmp.add(tok.nextToken());
                    }
                }
            }

            this.elements = tmp.toArray(new String[tmp.size()]);
        }
    }

    /**
     * Get the file name, or <code>null</code> if the pathname is empty.
     * 
     * @return the resulting file name or <code>null</code>.
     */
    public Pathname getFileName() {
        if (elements.length == 0) {
            return null;
        }
        
        return new Pathname(separator, elements[elements.length - 1]);
    }

    /**
     * Get the file name as a <code>String</code>, or <code>null</code> if the pathname is empty.
     * 
     * @return the resulting file name or <code>null</code>.
     */
    public String getFileNameAsString() {
        if (elements.length == 0) {
            return null;
        }
        
        return elements[elements.length - 1];
    }
    
    /**
     * Get the separator.
     * 
     * @return the separator.
     */
    public char getSeparator() {
        return separator;
    }

    /**
     * Get the parent path, or <code>null</code> if this path does not have a parent.
     * 
     * @return a path representing the path's parent.
     */
    public Pathname getParent() {

        if (elements.length == 0) {
            return null;
        }

        String[] parentElements = Arrays.copyOfRange(elements, 0, elements.length - 1);

        return new Pathname(separator, parentElements);
    }

    /**
     * Get the number of name elements in the path.
     * 
     * @return the number of elements in the path, or 0 if this is empty.
     */
    public int getNameCount() {
        return elements.length;
    }

    /**
     * Get a name element of this path.
     * 
     * @param index
     *            the index of the element
     * 
     * @return the name element
     * 
     * @throws IllegalArgumentException
     *             If the index is negative or greater or equal to the number of elements in the path.
     */
    public Pathname getName(int index) {
        if (index < 0 || index >= elements.length) {
            throw new IllegalArgumentException("index " + index + " not present in path " + this);
        }
        return new Pathname(separator, elements[index]);
    }

    /**
     * Returns a pathname that is a subsequence of the name elements of this path.
     * 
     * @param beginIndex
     *            the index of the first element, inclusive
     * @param endIndex
     *            the index of the last element, exclusive
     * 
     * @return a new pathname that is a subsequence of the name elements in this path.
     * 
     * @throws IllegalArgumentException
     *             If the beginIndex or endIndex is negative or greater or equal to the number of elements in the path, or if
     *             beginIndex is larger that or equal to the endIndex.
     */
    public Pathname subpath(int beginIndex, int endIndex) {

        if (beginIndex < 0 || beginIndex >= elements.length) {
            throw new IllegalArgumentException("beginIndex " + beginIndex + " not present in path " + this);
        }

        if (endIndex < 0 || endIndex > elements.length) {
            throw new IllegalArgumentException("endIndex " + endIndex + " not present in path " + this);
        }

        if (beginIndex >= endIndex) {
            throw new IllegalArgumentException("beginIndex " + beginIndex + " larger than endIndex " + endIndex);
        }

        String[] tmp = new String[endIndex - beginIndex];

        for (int i = beginIndex; i < endIndex; i++) {
            tmp[i - beginIndex] = elements[i];
        }

        return new Pathname(separator, tmp);
    }

    /**
     * Tests if this path starts with the given path.
     * 
     * This method returns <code>true</code> if this path starts with the same name elements as the given path. If the given path
     * has more name elements than this path then false is returned.
     * 
     * @param other
     *            the path to test.
     * 
     * @return If this paths start with the other path.
     */
    public boolean startsWith(Pathname other) {

        if (other.isEmpty()) {
            return true;
        }

        if (isEmpty()) {
            return false;
        }

        if (other.elements.length > elements.length) {
            return false;
        }

        for (int i = 0; i < other.elements.length; i++) {
            if (!other.elements[i].equals(elements[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests if this path ends with the given path.
     * 
     * This method returns <code>true</code> if this path end with the same name elements as the given path. If the given path
     * has more name elements than this path then false is returned.
     * 
     * @param other
     *            the path to test.
     * 
     * @return If this paths ends with the other path.
     */
    public boolean endsWith(Pathname other) {

        if (other.isEmpty()) {
            return true;
        }

        if (isEmpty()) {
            return false;
        }

        if (other.elements.length > elements.length) {
            return false;
        }

        int offset = elements.length - other.elements.length;

        for (int i = 0; i < other.elements.length; i++) {
            if (!other.elements[i].equals(elements[offset + i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Tests if this path starts with the given path.
     *
     * This method converts the <code>other</code> into a <code>Pathname</code> using {@link #Pathname(String)} and then uses 
     * {@link #startsWith(Pathname)} to compare the result to this path.
     * 
     * @param other
     *            the path to test.
     * 
     * @return If this paths start with the other path.
     */
    public boolean startsWith(String other) {
        return startsWith(new Pathname(other));
    }

    /**
     * Tests if this path ends with the given path.
     * 
     * This method converts the <code>other</code> into a <code>Pathname</code> using {@link #Pathname(String)} and then uses 
     * {@link #endsWith(Pathname)} to compare the result to this path.
     * 
     * @param other
     *            the path to test.
     * 
     * @return If this paths ends with the other path.
     */
    public boolean endsWith(String other) {
        return endsWith(new Pathname(other));
    }
    
    /**
     * Concatenate two String arrays.
     * 
     * @param a
     *            the first String array.
     * @param b
     *            the first String array.
     * 
     * @return the concatenation of the two String arrays.
     */
    private String[] merge(String[] a, String[] b) {

        int count = a.length + b.length;

        String[] tmp = new String[count];

        System.arraycopy(a, 0, tmp, 0, a.length);
        System.arraycopy(b, 0, tmp, a.length, b.length);

        return tmp;
    }

    /**
     * Resolve a pathname against this path.
     * 
     * If <code>other</code> represents an empty path, this path is returned.
     * 
     * If this path is empty, the <code>other</code> path is returned.
     * 
     * Otherwise, a new pathname is returned that contains the concatenation of the path elements this path and the
     * <code>other</code> path.
     * 
     * @param other
     *            the pathname.
     */
    public Pathname resolve(Pathname other) {

        if (other == null || other.isEmpty()) {
            return this;
        }

        if (isEmpty()) {
            return other;
        }

        return new Pathname(separator, merge(elements, other.elements));
    }

    /**
     * Resolve a String containing a pathname against this path.
     * 
     * If <code>other</code> represents an empty path, this path is returned.
     * 
     * If this path is empty, a pathname representing the <code>other</code> path is returned.
     * 
     * Otherwise, a new pathname is returned that contains the concatenation of the path elements this path and the
     * <code>other</code> path.
     * 
     * @param other
     *            the pathname.
     */
    public Pathname resolve(String other) {

        if (other == null || other.length() == 0) {
            return this;
        }

        return resolve(new Pathname(other));
    }

    /**
     * Is this path empty ?
     * 
     * @return If this path is empty.
     */
    public boolean isEmpty() {
        return elements.length == 0;
    }

    /**
     * Resolves the given path to this paths parent path, thereby creating a sibling to this path.
     * 
     * TODO: semantics ???
     * 
     * @param other
     *            the path to resolve as sibling.
     * 
     * @return a pathname representing the sibling.
     * 
     * @throws IllegalArgumentException
     *             If the path can not be resolved as a sibling to this path.
     */
    public Pathname resolveSibling(Pathname other) {

        if (isEmpty()) {
            if (other == null) {
                return this;
            } else {
                return other;
            }
        }

        Pathname parent = getParent();

        if (other == null || other.isEmpty()) {
            return parent;
        }

        return parent.resolve(other);
    }

    /**
     * Create a relative path between the given path and this path.
     * 
     * Relativation is the inverse of resolution. This method returns a path that, when resolved against this path, results in
     * the given path <code>other</code>.   
     * 
     * @param other
     *            the path to relativize.
     * 
     * @return a pathname representing a relative path between the given path and this path.
     * 
     * @throws IllegalArgumentException
     *             If the path can not be relativized to this path.
     */
    public Pathname relativize(Pathname other) {

        if (isEmpty()) {
            return other;
        }

        if (other.isEmpty()) {
            return this;
        }

        Pathname normalized = normalize();
        Pathname normalizedOther = other.normalize();

        String[] elts = normalized.elements;
        String[] eltsOther = normalizedOther.elements;

        // The source may not be longer that target
        if (elts.length > eltsOther.length) {
            throw new IllegalArgumentException("Cannot relativize " + other.getAbsolutePath() + " to " + getAbsolutePath());
        }

        // If source and target must have the same start.
        for (int i = 0; i < elts.length; i++) {
            if (!elts[i].equals(eltsOther[i])) {
                throw new IllegalArgumentException("Cannot relativize " + other.getAbsolutePath() + " to " + getAbsolutePath());
            }
        }

        if (elts.length == eltsOther.length) {
            return new Pathname(separator, new String[0]);
        }

        return new Pathname(separator, Arrays.copyOfRange(eltsOther, elts.length, eltsOther.length));
    }

    /**
     * Create an {@link Iterator} that returns all possible sub paths of this path, in order of increasing length.
     * 
     * For example, for the path "/a/b/c/d" the iterator returns "/a", "/a/b", "a/b/c", "/a/b/c/d".
     * 
     * @return the iterator.
     */
    public Iterator<Pathname> iterator() {
        return new PathnameIterator();
    }

    /**
     * Return a <code>String</code> representation of this interpreted as a relative path.
     * 
     * A relative path does not start with a separator. 
     * 
     * @return a String representation of this path interpreted as a relative path.
     */
    public String getRelativePath() {

        if (elements.length == 0) {
            return "";
        }

        StringBuilder tmp = new StringBuilder();

        String sep = "";
        
        for (int i = 0; i < elements.length; i++) {
            tmp.append(sep);
            tmp.append(elements[i]);
            sep = "" + separator;
        }
        
        return tmp.toString();
    }

    /**
     * Return a <code>String</code> representation of this interpreted as an absolute path.
     * 
     * An absolute path starts with a separator. 
     * 
     * @return a String representation of this path interpreted as an absolute path.
     */
    public String getAbsolutePath() {
        
        if (elements.length == 0) {
            return "" + separator;
        }

        StringBuilder tmp = new StringBuilder();

        for (int i = 0; i < elements.length; i++) {
            tmp.append(separator);
            tmp.append(elements[i]);
        }
        
        return tmp.toString();
    }

    
    /**
     * Normalize this pathname by removing as many redundant path elements as possible.
     * 
     * Redundant path elements are <code>"."</code> (indicating the current directory) and <code>".."</code> (indicating the
     * parent directory).
     * 
     * Note that the resulting normalized path does may still contain <code>".."</code> elements which are not redundant.
     * 
     * @return the normalize path.
     */
    public Pathname normalize() {

        if (isEmpty()) {
            return this;
        }

        ArrayList<String> stack = new ArrayList<String>();

        for (String s : elements) {
            stack.add(s);
        }

        boolean change = true;

        while (change) {
            change = false;

            for (int i = stack.size() - 1; i >= 0; i--) {

                if (i < stack.size()) {

                    String elt = stack.get(i);

                    if (elt.equals(".")) {
                        stack.remove(i);
                        change = true;

                    } else if (elt.equals("..")) {

                        if (i > 0) {
                            String parent = stack.get(i - 1);

                            if (!(parent.equals(".") || parent.equals(".."))) {
                                // NOTE: order is VERY important here!
                                stack.remove(i);
                                stack.remove(i - 1);
                                change = true;
                            }
                        }
                    }
                }
            }
        }

        String[] tmp = stack.toArray(new String[stack.size()]);
        return new Pathname(separator, tmp);
    }

    /* Generated */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(elements);
        result = prime * result + separator;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        Pathname other = (Pathname) obj;

        if (separator != other.separator) {
            return false;
        }

        return Arrays.equals(elements, other.elements);
    }

    @Override
    public String toString() {
        return "Pathname [element=" + Arrays.toString(elements) + ", seperator=" + separator + "]";
    }
}