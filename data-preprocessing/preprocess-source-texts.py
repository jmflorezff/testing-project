#!/usr/bin/env python3

"""
Applies tokenizing, identifier splitting, stop word and java terms removal on
previously extracted source file texts.

Input format: {"file": ..., "text": ...}
"""


from preprocessing import Preprocessor
import os
import json
import sys


# Terms we want to ignore: java API class names, stop words and java keywords
IGNORE_TERMS_FILE_NAMES = (
    'stop-words.txt',
    'java-keywords.txt',
)


def main():
    # Directory where this script is located
    dirname = os.path.dirname(__file__)

    # List of terms to be ignored by the tokenizer
    ignore_terms = []

    # Collect the terms we want to ignore
    for ignore_file_name in IGNORE_TERMS_FILE_NAMES:
        with open(os.path.join(dirname, ignore_file_name)) as file:
            ignore_terms.extend(term.strip() for term in file)
            
    # Create our custom tokenizer, it receives the terms we want to ignore
    preprocessor = Preprocessor(word_chars='a-zA-Z0-9', inter_chars="'",
                                min_length=3, ignore=ignore_terms)
    
    for line in sys.stdin:
        src_file = json.loads(line)
        old_text = src_file['text']

        src_file['text'] = ' '.join(preprocessor.preprocess(old_text))

        print(json.dumps(src_file))


if __name__ == '__main__':
    main()
