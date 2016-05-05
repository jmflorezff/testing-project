#!/usr/bin/env python3

"""
Utilities for preprocessing of source code and bug reports.
"""


import inflection
import nltk
import re


class Preprocessor(object):
    def __init__(self, word_chars=None, split_chars=None,
                 inter_chars=None, min_length=3, ignore=None):
        if not word_chars:
            word_chars = r'\w$'

        if not split_chars:
            split_chars = r'_$-'

        if not inter_chars:
            inter_chars = r"'-"
            
        if ignore:
            self.ignore = set(e.lower() for e in ignore)
        else:
            self.ignore = []
        self.strip_chars = split_chars + inter_chars
        self.min_length = min_length
        self.id_split_re = re.compile(r'[%s]' % split_chars)
        self.tokenizer = nltk.tokenize.RegexpTokenizer(
            r"[{0}]+(?:[{1}][{0}]+)*".format(word_chars, inter_chars))
        self.stemmer = nltk.stem.PorterStemmer()

    def is_numeric(self, token):
        """Returns True if at least min_length characters in the strings are
        letters. Intentionally admits punctuation.
        """
        letters = 0
        numbers = 0
        
        for c in token:
            if c.isalpha():
                letters += 1
            elif c.isdigit():
                numbers += 1

        if letters < self.min_length:
            return True
        elif letters > numbers:
            return False

        return True

    def is_valid_token(self, token):
        """A token is valid if it is longer than the minimum length, is not
        numeric and is not in the ignore list.
        """
        return (len(token) >= self.min_length and not self.is_numeric(token)
                and token.lower() not in self.ignore)
    
    def preprocess(self, *strings):
        """Takes an iterable of strings of arbitrary length and contents
        and applies tokenization and identifier splitting, also validating
        minimum length and ignored words before and after splitting.
        """
        
        all_tokens = []
        for string in strings:
            if not string:
                continue
            
            tokens = self.tokenizer.tokenize(string)
            for token in tokens:
                if not self.is_valid_token(token):
                    continue

                new_tokens = [token.lower()]

                # Attempt to split the token
                splits = [word for word in
                          self.id_split_re.split(inflection.underscore(token))
                          if word]
                
                if len(splits) > 1:
                    new_tokens.extend(s for s in splits
                                      if self.is_valid_token(s))
                
                # Stem all new tokens and get rid of delimiters in the edges
                # of words
                stemmed = [self.stemmer.stem(t).strip(self.strip_chars)
                           for t in new_tokens]

                # Add the stemmed tokens if they are valid after stemming
                all_tokens.extend(s for s in stemmed
                                  if self.is_valid_token(s))

        return all_tokens
