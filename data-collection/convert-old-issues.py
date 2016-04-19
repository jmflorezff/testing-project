#!/usr/bin/env python3

"""
Converts and issues in the old format to JSON while removing test files and
converting class paths to file paths.
"""


import json
import os
import re
import sys


SRC_ROOTS = {
    'BOOKKEEPER': '/home/juan/Source/bookkeeper-4.1.0',
    'DERBY': '/home/juan/Source/db-derby-10.9.1.0-src',
    'LUCENE': '/home/juan/Source/lucene-solr-releases-lucene-solr-4.4.0',
    'MAHOUT': '/home/juan/Source/mahout-distribution-0.8',
    'OPENJPA': '/home/juan/Source/openjpa-2.2.0',
    'PIG': '/home/juan/Source/pig-release-0.11.1',
    'SOLR': '/home/juan/Source/lucene-solr-releases-lucene-solr-4.4.0',
    'TIKA': '/home/juan/Source/tika-1.3',
    'ZOOKEEPER': '/home/juan/Source/zookeeper-release-3.4.5',
}    


def main():
    test_file_re = re.compile(os.sep + 'test' + os.sep)
    while True:
        line = sys.stdin.readline().strip()
        
        if not line:
            break
        
        _, key = line.split()
        system = key.split('-')[0]

        # Skip lines until a line with a single number in it is found
        while True:
            line = sys.stdin.readline()
            try:
                amount_of_files = int(line)
                break
            except:
                pass
            
        output_file_paths = set()

        for file_index in range(amount_of_files):
            class_path = (sys.stdin.readline().strip().replace('.', os.sep) +
                          '.java')
            
            if test_file_re.search(class_path):
                print('Ignoring test file: %s' % class_path,
                      file=sys.stderr)
                continue
                      
            presumed_file_path = os.path.join(SRC_ROOTS[system], class_path)
            nesting_level = 0

            while nesting_level < 3:
                if os.path.exists(presumed_file_path):
                    break

                class_path = (class_path[:presumed_file_path.rindex(os.sep)] +
                              '.java')
                presumed_file_path = os.path.join(SRC_ROOTS[system], class_path)
                nesting_level += 1
            else:
                print("WARNING: Path for '%s' does not exist in issue %s" %
                      (class_path, key), file=sys.stderr)
                continue

            output_file_paths.add(class_path)

        if not output_file_paths:
            print("WARNING: No fixed files for issue %s" % key,
                  file=sys.stderr)
        else:
            print(json.dumps({'key': key,
                              'fixed_files': list(output_file_paths)}))
                
        sys.stdin.readline()


if __name__ == '__main__':
    main()
