# -*- coding: utf-8 -*-
from collections import defaultdict
import shutil
import json
import uuid

from jinja2 import Environment, FileSystemLoader
from pygments import highlight
from pygments.lexers import CLexer
from pygments.formatters import HtmlFormatter


class LinkedHtmlFormatter(HtmlFormatter):
    def __init__(self, **kwargs):
        HtmlFormatter.__init__(self, **kwargs)
        self.hl_links = kwargs['hl_links']
        self.fileid = kwargs['fileid']

    def wrap(self, source, outfile):
        return self._wrap_code(source)

    def _wrap_code(self, source):
        yield 0, '<div class="' + self.fileid + '"><pre>'
        lineno = 0
        for (i, txt) in source:
            if i == 1:
                lineno += 1
                if lineno in self.hl_lines:
                    yield 0, '<div class="line-with-errors">'
                    if len(self.hl_links[lineno]) == 1:
                        err = self.hl_links[lineno][0]
                        ix = err[u'index']
                        tgt = 'error-%d.html' % ix
                        yield 0, '<a class="hll" href="%s">' % tgt
                        yield i, txt
                        yield 0, '</a>'
                    else:
                        yield i, txt
                    yield 0, '<div class="errors">'
                    for err in self.hl_links[lineno]:
                        yield 0, '<span class="lineno">   </span>'
                        msg = '%s (%s)' % (err[u'description'], err[u'error_id'])
                        ix = err[u'index']
                        tgt = 'error-%d.html' % ix
                        line = '<a href="%s"> %s Error #%d</a>\n' % (tgt, msg, ix)
                        yield 0, line
                    yield 0, '</div></div>'
                else:
                    yield 0, '<span class="line-%d line">' % lineno
                    yield i, txt
                    yield 0, '</span>'
            else:
                yield i, txt
        yield 0, '</pre></div>'


def highlight_c(code, hl_links, fileid):
    hl_lines = hl_links.keys()
    return highlight(code,
                     CLexer(),
                     LinkedHtmlFormatter(hl_lines=hl_lines, hl_links=hl_links, linenos='inline', fileid=fileid))


def load_json(filename):
    with open(filename, 'r') as f:
        for line in f:
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                print('error reading: ' + line)


errors = list(load_json('errors.json'))
files = {}
datafiles = []
global_data = {item: load_json(item + '.json')
               for item in datafiles}


def listdict():
    return defaultdict(list)

def null_loc():
    return {
        u'rel_file': u'missing',
        u'abs_file': u'missing',
        u'line': 0,
        u'column': 0,
        u'system_header': False,
    }

def null_frame():
    return {
        u'symbol': u'unknown',
        u'loc': null_loc(),
        u'locks': [],
        u'language': u'C',
    }

errorsByFile = defaultdict(listdict)
for ix, err in enumerate(errors):
    err[u'index'] = ix
    err[u'errdesc'] = '%s (%s)' % (err[u'description'], err[u'error_id'])
    if (u'traces' in err):
        traces = err[u'traces']
        if traces:
            frames = traces[0][u'frames']
            if not frames:
                frames.append(null_frame())
        else:
            err[u'traces'] = [{u'frames': [null_frame()]}]
        frame = traces[0][u'frames'][0]
        for tr in traces:
            for fr in tr[u'frames']:
                if (u'loc' in fr):
                    loc = fr[u'loc']
                    if loc[u'rel_file'] != "missing":
                        files[loc[u'abs_file']] = loc[u'rel_file']
    else:
        frame = err[u'loc']
        err[u'traces']=[{u'frames': [frame]}]
        if (u'loc' in frame):
            loc = frame[u'loc']
            if loc[u'rel_file'] != "missing":
                files[loc[u'abs_file']] = loc[u'rel_file']
    if (u'loc' in frame):
        loc = frame[u'loc']
        err[u'file'] = loc[u'rel_file']
        err[u'line'] = loc[u'line']
    else:
        err[u'file'] = u'missing'
        err[u'line'] = 0
        frame[u'loc'] = null_loc()
    err[u'function'] = frame[u'symbol']
    errorsByFile[err[u'file']][err[u'line']].append(err)


def render_code(rel, abs, path, errors):
    filename = abs
    with open(filename, 'r') as f, open('output/' + path + '.html', 'w') as target:
        code = f.read()
        template = env.get_template('sourcefile.html')
        if errors:
            errorlines = errorsByFile[rel]
        else:
            errorlines = {}
        target.write(template.render(filename=filename,
                                     errorlines=errorlines,
                                     code=code,
                                     info=rel,
                                     fileid=path))


def render_error(err):
    template = env.get_template('error.html')
    with open('output/error-%d.html' % err[u'index'], 'w') as target:
        target.write(template.render(err=err, fileids=fileids))


def render_template(filename):
    with open('output/' + filename, 'w') as target:
        template = env.get_template(filename)
        target.write(template.render())


env = Environment(
    autoescape=True,
    loader=FileSystemLoader('templates/')
)
env.filters[u'highlight'] = highlight_c

env.globals.update(global_data)
env.globals[u'files'] = sorted(files)
env.globals[u'errors'] = errors

fileids = {}

shutil.rmtree('output/', True)
shutil.copytree('static/', 'output/static/')
for template in ['index.html', 'error-descriptions.html']:
    render_template(template)
for abs,rel in files.iteritems():
    render_code(rel,abs,rel,True)
    fileids[abs] = str(uuid.uuid4())
    render_code(rel,abs,fileids[abs], False)
for err in errors:
    render_error(err)
with open("output/errors.json.js", "w") as file:
    file.write("var json_data='" + json.dumps(errors).replace('\\"',"'").replace("'", "\\'") + "';\n");
